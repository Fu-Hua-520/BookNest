package com.fuhua.booknest.server.common;

import com.fuhua.booknest.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 计数缓冲 —— 给「不要求强一致性」的计数做「先加 Redis、定时批量落库」的写入缓冲。
 *
 * <h3>为什么值得这么做</h3>
 * 浏览、点赞、收藏、评论这类计数有三个共同点：
 * <ol>
 *   <li><b>写极频繁</b>：打开一次详情页就是一次 {@code view_count} 更新，点赞更是秒杀级热点；</li>
 *   <li><b>语义只是累加</b>：只有 +1 / -1，不存在「必须立刻读到刚写进去的值」的业务；</li>
 *   <li><b>不要求强一致</b>：晚几秒看到数字变化，没有任何人会因此做错事。</li>
 * </ol>
 * 把「每次写都打一次 MySQL」换成「在 Redis 里累加，由 {@code CountFlushJob} 定时批量落库」，
 * 收益是热点行的<b>行锁竞争被彻底拿掉</b>：一万次浏览从一万条 UPDATE 变成一条
 * {@code view_count = view_count + 10000}。
 *
 * <h3>数据放在哪、怎么做到不丢、又怎么做到不重</h3>
 * 所有待落库增量集中在<b>一个 HASH</b>（{@link RedisConstant#COUNT_PENDING}）里，
 * field = {@code 类型名:业务ID}，value = 待累加增量（HINCRBY 原子累加）。
 * 落库采用「换出 → 登记 → 写库 → 丢弃」，而不是「读出来 → 写库 → 删掉条目」：
 * <pre>
 *   HSET   bn:count:pending __batch &lt;UUID&gt;         // ① 先定批次身份（已有则跳过）
 *   RENAME bn:count:pending → bn:count:flushing     // ② 原子换出：身份与增量一起被搬走
 *   HGETALL bn:count:flushing                       // ③ 读出本批
 *   INSERT count_flush_ledger(batch_id) + UPDATE .. // ④ 登记与落库在同一个事务里
 *   DEL bn:count:flushing                           // ⑤ 确认成功后才丢弃
 * </pre>
 *
 * <p><b>不丢</b>：RENAME 是原子的 —— 换出的那一瞬间，后续写入不可能再进到这批数据里，
 * 所以「读出来再逐条 HDEL」那种写法会丢的增量，在这里不会丢。
 * 在「已换出、未落库」之间挂掉时，数据仍留在 {@code flushing} 里，
 * 下一轮由 {@code CountFlushJob} 当「残留」重新落一次。</p>
 *
 * <p><b>不重</b>：重落的依据是批次ID —— 它写在 HASH 里（{@link RedisConstant#COUNT_PENDING_META_BATCH}），
 * 由 RENAME 与增量原子地一起换出，所以残留批次永远自带身份。落库时先在 MySQL 的
 * {@code count_flush_ledger} 里按这个ID 登记；登记不上（主键冲突）说明<b>本批已经写过了</b>，
 * 于是只做「确认丢弃」而不再碰计数列。被关掉的就是「写库成功、但确认删除失败」
 * 这条唯一会重复累加的路（详见 {@code CountFlushJob.writeToDb}）。</p>
 *
 * <p>为什么账本必须落在 MySQL 而不是 Redis：登记与写库必须原子。放到 Redis 里就
 * 又变成两个存储的两阶段提交 —— 登记成功而写库失败会丢计数，写库成功而登记失败
 * 会重复计数。只有同一个数据库事务能同时持有这两件事。</p>
 *
 * <h3>失败策略：与缓存相反，这里是「悲观」的</h3>
 * {@link RedisCacheHelper} 是 fail-open（Redis 挂了就当未命中，回源查库，结果依然正确）。
 * 计数缓冲<b>没有回源这一说</b> —— 一次 {@code addDelta} 失败就等于这一笔计数丢了，
 * 落库任务停摆就等于计数停止增长。所以这里：
 * <ul>
 *   <li>写失败只记日志、<b>绝不回滚业务</b>。点赞成功而计数少 1，比「点赞失败」可接受得多；</li>
 *   <li>容忍最终少算：这是「不要求强一致性」的直接代价，也是这个方案唯一真正的风险点
 *       （只在 Redis 真丢数据时发生，见 {@code CountFlushJob} 的取舍说明）；</li>
 *   <li>落库失败时<b>保留</b>数据（不 DEL），宁可多跑一次幂等账本，也不丢。</li>
 * </ul>
 *
 * <h3>读路径必须配合</h3>
 * 增量还在 Redis 里时，数据库的列是落后的。所以<b>任何对外展示这些计数的地方，
 * 都要用 {@code 行里的值 + pending 增量}</b>（见 {@link #merge} 与
 * {@link #pendingPostCounts}）。否则用户会看到「刚点的赞没算上」。
 *
 * <h3>为什么不给缓冲键设 TTL</h3>
 * 它装的是<b>还没落库的数据</b>，过期就等于凭空丢计数。它也不会无限增长：
 * 只有在应用运行时才有人往里写，而应用运行时落库任务一直在跑。
 */
@Component
@Slf4j
public class CountBuffer {

    /**
     * 计数类型 —— 每种类型对应「一张表的一个计数列」。
     *
     * <p>枚举名会作为缓冲 field 的前缀写进 Redis（{@code POST_VIEW:xxx}），
     * 所以<b>改名等于让旧缓冲数据变成无法识别的孤儿</b>（{@code typeOf} 返回 null，落库时跳过并告警）。
     * 部署时若碰到「改过枚举名」的情况，提前把 {@link RedisConstant#COUNT_PENDING} 落一次库或直接清掉即可。</p>
     */
    public enum CounterType {
        /** 帖子浏览量 */
        POST_VIEW("post", "view_count"),
        /** 帖子点赞数 */
        POST_LIKE("post", "like_count"),
        /** 帖子评论数 */
        POST_COMMENT("post", "comment_count"),
        /** 帖子收藏数 */
        POST_COLLECT("post", "collect_count"),
        /** 评论点赞数 */
        COMMENT_LIKE("post_comment", "like_count");

        private final String table;
        private final String column;

        CounterType(String table, String column) {
            this.table = table;
            this.column = column;
        }

        /** 所在表（仅用于日志与排错，落库走各 Mapper 的显式 SQL） */
        public String table() {
            return table;
        }

        /** 所在列（仅用于日志与排错） */
        public String column() {
            return column;
        }
    }

    /**
     * 一页帖子的四类待落库增量。
     *
     * <p>{@code 行里的值 + 这里的值 = 当前应该展示的计数}。</p>
     */
    public record PostCountDelta(long view, long like, long comment, long collect) {
        /** 没有未落库增量时的占位值 */
        public static final PostCountDelta ZERO = new PostCountDelta(0L, 0L, 0L, 0L);
    }

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /* ============================== 写路径 ============================== */

    /**
     * 记一笔待落库增量（+1 / -1 / -N）。
     *
     * <p><b>必须放在事务提交之后调用</b>（用 {@code runAfterCommit}）。理由与「慢活出事务」一致：
     * 事务一旦回滚，业务动作（点赞行、评论行）根本不存在，这笔增量就不该留下 ——
     * 而 Redis 不参与事务，写进去就退不回来了。</p>
     *
     * @param type  计数类型
     * @param id    业务ID（帖子ID / 评论ID）
     * @param delta 增量，可为负
     */
    public void addDelta(CounterType type, String id, long delta) {
        if (type == null || id == null || id.isEmpty() || delta == 0L) {
            return;
        }
        try {
            stringRedisTemplate.opsForHash().increment(
                    RedisConstant.COUNT_PENDING, field(type, id), delta);
        } catch (Exception e) {
            // 不回滚业务：点赞成功但计数少 1，远好过点赞直接失败
            log.warn("计数缓冲写入失败（本次增量丢失）: {}={} delta={}, err={}",
                    type, id, delta, e.getMessage());
        }
    }

    /* ============================== 读路径 ============================== */

    /** 读某一条记录还没落库的增量（没有则 0） */
    public long pending(CounterType type, String id) {
        if (type == null || id == null || id.isEmpty()) {
            return 0L;
        }
        try {
            Object raw = stringRedisTemplate.opsForHash().get(RedisConstant.COUNT_PENDING, field(type, id));
            return parse(raw);
        } catch (Exception e) {
            log.warn("读计数缓冲失败，按 0 处理: {}={}, err={}", type, id, e.getMessage());
            return 0L;
        }
    }

    /**
     * 批量读同类增量（一次 HMGET，N 条记录只花 1 次往返）。
     *
     * @return 业务ID → 增量；<b>永远非 null</b>，无增量的记录不在 Map 里
     */
    public Map<String, Long> pendingBatch(CounterType type, Collection<String> ids) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (type == null || ids == null || ids.isEmpty()) {
            return result;
        }
        List<String> fields = new ArrayList<>(ids.size());
        List<String> orderedIds = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            orderedIds.add(id);
            fields.add(field(type, id));
        }
        if (fields.isEmpty()) {
            return result;
        }
        try {
            List<String> values = stringRedisTemplate.<String, String>opsForHash()
                    .multiGet(RedisConstant.COUNT_PENDING, fields);
            if (values == null) {
                return result;
            }
            for (int i = 0; i < orderedIds.size() && i < values.size(); i++) {
                long delta = parse(values.get(i));
                if (delta != 0L) {
                    result.put(orderedIds.get(i), delta);
                }
            }
        } catch (Exception e) {
            log.warn("批量读计数缓冲失败，全部按 0 处理: type={}, count={}, err={}",
                    type, orderedIds.size(), e.getMessage());
        }
        return result;
    }

    /**
     * 一次 HMGET 读回一批帖子的<b>四种</b>增量（浏览 / 点赞 / 评论 / 收藏）。
     *
     * <p>列表页一页 10 篇帖子，四类增量合起来是 40 个 field，
     * 打包成一次 HMGET 比调 4 次 {@link #pendingBatch} 少 3 次往返。</p>
     *
     * @param postIds 帖子ID集合
     * @return 帖子ID → 四类增量；<b>永远非 null</b>，且每个入参 ID 都有值（无增量时为 {@link PostCountDelta#ZERO}）
     */
    public Map<String, PostCountDelta> pendingPostCounts(Collection<String> postIds) {
        Map<String, PostCountDelta> result = new LinkedHashMap<>();
        if (postIds == null || postIds.isEmpty()) {
            return result;
        }
        for (String postId : postIds) {
            if (postId != null && !postId.isEmpty()) {
                result.put(postId, PostCountDelta.ZERO);
            }
        }
        if (result.isEmpty()) {
            return result;
        }
        try {
            List<String> fields = new ArrayList<>(result.size() * 4);
            for (String postId : result.keySet()) {
                fields.add(field(CounterType.POST_VIEW, postId));
                fields.add(field(CounterType.POST_LIKE, postId));
                fields.add(field(CounterType.POST_COMMENT, postId));
                fields.add(field(CounterType.POST_COLLECT, postId));
            }
            List<String> values = stringRedisTemplate.<String, String>opsForHash()
                    .multiGet(RedisConstant.COUNT_PENDING, fields);
            if (values == null) {
                return result;
            }
            int idx = 0;
            for (String postId : result.keySet()) {
                if (idx + 3 >= values.size()) {
                    break;
                }
                long view = parse(values.get(idx));
                long like = parse(values.get(idx + 1));
                long comment = parse(values.get(idx + 2));
                long collect = parse(values.get(idx + 3));
                if (view != 0L || like != 0L || comment != 0L || collect != 0L) {
                    result.put(postId, new PostCountDelta(view, like, comment, collect));
                }
                idx += 4;
            }
        } catch (Exception e) {
            log.warn("批量读帖子计数缓冲失败，全部按 0 处理: count={}, err={}",
                    result.size(), e.getMessage());
        }
        return result;
    }

    /**
     * 「行里的值 + 未落库增量」= 当前该展示的值（浏览量版）。
     *
     * @param dbValue      数据库里读出来的值，可为 null（当成 0）
     * @param pendingDelta 未落库增量
     */
    public static long merge(Long dbValue, long pendingDelta) {
        return (dbValue == null ? 0L : dbValue) + pendingDelta;
    }

    /**
     * 「行里的值 + 未落库增量」= 当前该展示的值（点赞/评论/收藏版）。
     *
     * <p>理论上不该出现负数（增量与业务行一一对应），但一旦数据库里是历史脏数据
     * （例如 like_count 为 0 却有一堆点赞行），扣减就会把它压到负数。
     * 展示层兜一下底，比让用户看到「-1 赞」体面。</p>
     */
    public static int merge(Integer dbValue, long pendingDelta) {
        long merged = (dbValue == null ? 0L : dbValue) + pendingDelta;
        if (merged < 0L) {
            return 0;
        }
        return merged > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) merged;
    }

    /* ============================ 落库路径 ============================ */

    /**
     * 一批待落库的增量 —— 可能来自 pending 的换出，也可能是上一轮留下的残留。
     *
     * @param batchId 批次ID（UUID）。落库幂等的依据：MySQL 的 {@code count_flush_ledger}
     *                以它为主键，同一个批次最多只生效一次。为 null 表示这个残留没有身份
     *                （升级前遗留的残留、或人工改过键），落库侧只能按「新批次」处理，幂等对它失效
     * @param entries field → 增量；已剔除 {@code __batch} 元字段与净零增量
     */
    public record Batch(String batchId, Map<String, Long> entries) {
        /** 有没有真正需要落库的增量 */
        public boolean isEmpty() {
            return entries.isEmpty();
        }

        /** 条目数（打日志用） */
        public int size() {
            return entries.size();
        }
    }

    /**
     * 是否存在「已换出、未确认」的残留批次。
     *
     * <p>判断「要不要先处理残留」必须用它，不能用 {@code leftover() == null || isEmpty()}：
     * 残留里可能<b>全是净零增量</b>（同一窗口内 +1 又 -1，HINCRBY 会把 field 留在 0），
     * 那时读出来的条目是空的，可残留键确实还在 —— 而 {@link #drain()} 见到残留就不换出新批次，
     * 于是「没有数据要落」与「残留占着位」互相锁死，落库永久停摆。</p>
     *
     * @return 有残留返回 true；<b>读不出来也返回 true</b>（保守：宁可本轮什么都不做，
     *         也不要冒险把可能存在的残留覆盖掉）
     */
    public boolean hasLeftover() {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.COUNT_FLUSHING));
        } catch (Exception e) {
            log.warn("检查计数残留失败，本轮按「有残留」处理: {}", e.getMessage());
            return true;
        }
    }

    /**
     * 读上一轮留下的残留批次。
     *
     * @return 残留批次；<b>null 表示读失败</b>（Redis 异常）—— 调用方必须停手，
     *         绝不能把它当成「没有残留」而顺手确认删除；{@code entries} 为空但非 null 的
     *         Batch 才是「残留确实存在、且已无净增量」，那时才可以安全确认丢弃
     */
    public Batch leftover() {
        return readBatch(RedisConstant.COUNT_FLUSHING);
    }

    /**
     * 原子换出全部待落库增量，返回本批数据。
     *
     * <p><b>前置条件：残留（{@link #hasLeftover()}）必须已经被处理并确认。</b>
     * 否则 RENAME 会把残留的那批直接覆盖掉（= 静默丢计数）。这里仍然自己再检查一遍，
     * 拿不到就放弃 —— 防线写在最靠近危险动作的地方，比只依赖调用方的顺序更可靠。</p>
     *
     * @return 本批；<b>null 表示本轮没有东西可落</b>（没有 pending、存在残留、Redis 异常，
     *         或本批净增量为零且已就地确认丢弃）
     */
    public Batch drain() {
        try {
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.COUNT_FLUSHING))) {
                log.warn("计数缓冲里还存在上一轮未落库的残留，本轮不换出新的（等残留落完再说）");
                return null;
            }
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.COUNT_PENDING))) {
                return null;
            }
            // 批次身份必须在 RENAME **之前**写进 pending：RENAME 只能搬一个键，
            // 身份若等换出之后再补写，就多出一段「写身份」与「写 MySQL」之间的窗口 ——
            // 那一小段里 Redis 若丢了身份而 MySQL 已经提交，残留就成了认不出、因而无法幂等的孤儿。
            // 写在 pending 里，RENAME 会把身份与增量原子地一起搬走，那个窗口根本不存在。
            if (!ensureBatchId()) {
                return null;
            }
            stringRedisTemplate.rename(RedisConstant.COUNT_PENDING, RedisConstant.COUNT_FLUSHING);
        } catch (Exception e) {
            log.warn("换出计数缓冲失败，本轮跳过: {}", e.getMessage());
            return null;
        }
        Batch batch = readBatch(RedisConstant.COUNT_FLUSHING);
        if (batch != null && batch.isEmpty()) {
            // 净零批次：整批增量在同一窗口内互相抵消，没有任何东西需要落库。
            // 但它会一直占着残留位，把后面所有批次挡在门外（见 hasLeftover 的说明），
            // 所以就地确认丢弃，而不是把「空批要不要确认」这个判断推给调用方。
            log.debug("本批净增量为零（同一窗口内加的又减回去了），直接确认丢弃");
            confirmDrained();
            return null;
        }
        return batch;
    }

    /**
     * 确保 {@code pending} 里带着本批的批次身份；没有就生成一个补上。
     *
     * @return 成功返回 true；Redis 异常返回 false（调用方应放弃换出：一个没有身份的残留
     *         没法靠账本幂等，只能盲落，那正是我们要避免的）
     */
    private boolean ensureBatchId() {
        try {
            Object existing = stringRedisTemplate.<String, String>opsForHash()
                    .get(RedisConstant.COUNT_PENDING, RedisConstant.COUNT_PENDING_META_BATCH);
            if (existing != null && !existing.toString().isBlank()) {
                return true;
            }
            String batchId = UUID.randomUUID().toString();
            stringRedisTemplate.<String, String>opsForHash()
                    .put(RedisConstant.COUNT_PENDING, RedisConstant.COUNT_PENDING_META_BATCH, batchId);
            log.debug("给本批计数分配批次ID: {}", batchId);
            return true;
        } catch (Exception e) {
            log.warn("写计数批次ID失败，本轮不换出（无身份的残留无法幂等落库）: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 确认本批已成功落库，丢弃它；<b>失败时不要调用</b>，让数据留到下一轮重试。
     *
     * <p>删除失败正是「写库成功但确认失败」这个窗口的入口：残留还在，下一轮会重落一遍。
     * 现在它已经被 MySQL 侧的幂等账本兜住（重落不会重复累加），但能当场删掉就不必多跑一遍账本流程，
     * 所以这里立刻重试几次，把网络抖动的瞬时失败挡在最外面。</p>
     */
    public void confirmDrained() {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                stringRedisTemplate.delete(RedisConstant.COUNT_FLUSHING);
                return;
            } catch (Exception e) {
                if (attempt < 3) {
                    log.debug("清理计数缓冲失败，第 {} 次重试: {}", attempt, e.getMessage());
                    sleepQuietly(100);
                } else {
                    log.warn("清理已落库的计数缓冲失败（已重试 3 次；下一轮会重落，由幂等账本保证不重复累加）: {}",
                            e.getMessage());
                }
            }
        }
    }

    /** 短暂停顿；被中断就恢复中断标志（重试间隔用，别把调度线程的中断位吞掉） */
    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* ============================== 落库互斥 ============================== */

    /**
     * 拿「落库令牌」，同一时刻只允许一个实例落库。
     *
     * <p>单实例部署时它是多余的（{@code @Scheduled(fixedDelay)} 本身就不会并发执行），
     * 但项目里别处已经按多实例在考虑（见 {@code BAR_HOT} 的注释），
     * 而这里的多实例故障模式很隐蔽：两个实例同时看到 {@code flushing} 有残留，
     * 就会各落一次库、把同一批增量<b>累加两次</b>。一把锁就能堵掉。</p>
     *
     * @return 拿到返回 true；Redis 异常时返回 false（少落一轮不影响正确性，下轮还有机会）
     */
    public boolean tryAcquireFlushLock() {
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                    RedisConstant.COUNT_FLUSH_LOCK, "1",
                    RedisConstant.COUNT_FLUSH_LOCK_TTL, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("获取计数落库令牌失败，本轮跳过: {}", e.getMessage());
            return false;
        }
    }

    /** 释放落库令牌（TTL 只是兜底：进程被 kill 时锁会自己过期） */
    public void releaseFlushLock() {
        try {
            stringRedisTemplate.delete(RedisConstant.COUNT_FLUSH_LOCK);
        } catch (Exception e) {
            log.debug("释放计数落库令牌失败（等 TTL 自然过期）: {}", e.getMessage());
        }
    }

    /* ============================== 其它 ============================== */

    /**
     * 把一个 field 解析回计数类型。
     *
     * @param field 形如 {@code POST_VIEW:8f14e45f-...}
     * @return 类型；<b>无法识别时返回 null</b>（枚举改过名、键空间里混进了别的东西），调用方应跳过并告警
     */
    public static CounterType typeOf(String field) {
        if (field == null) {
            return null;
        }
        int split = field.indexOf(':');
        if (split <= 0) {
            return null;
        }
        try {
            return CounterType.valueOf(field.substring(0, split));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 从一个 field 里取出业务ID。
     *
     * <p>用<b>第一个</b>冒号切分：类型名里不含冒号，所以ID里即便有冒号也会被完整保留。</p>
     *
     * @param field 形如 {@code POST_VIEW:8f14e45f-...}
     * @return 业务ID；格式不对时返回 null
     */
    public static String idOf(String field) {
        if (field == null) {
            return null;
        }
        int split = field.indexOf(':');
        if (split <= 0 || split == field.length() - 1) {
            return null;
        }
        return field.substring(split + 1);
    }

    /** 拼缓冲 field：{@code 类型名:业务ID} */
    public static String field(CounterType type, String id) {
        return type.name() + ":" + id;
    }

    /**
     * 读出一个批次 HASH 的内容：取出批次身份，剔掉元字段与净零增量。
     *
     * @return 批次；<b>null 表示读失败</b>（Redis 异常）——
     *         与「键不存在 / 键在但没有增量」区分开，因为调用方对这两种情况的处理完全相反：
     *         读失败必须停手，读到了空才可以确认丢弃
     */
    private Batch readBatch(String key) {
        try {
            Map<String, String> raw = stringRedisTemplate.<String, String>opsForHash().entries(key);
            if (raw == null || raw.isEmpty()) {
                // 键本来就不存在（正常空闲）：这不是「读失败」，而是一个没有内容的批次
                return new Batch(null, Map.of());
            }
            String batchId = null;
            Map<String, Long> entries = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                if (RedisConstant.COUNT_PENDING_META_BATCH.equals(entry.getKey())) {
                    batchId = entry.getValue();
                    continue;
                }
                long delta = parse(entry.getValue());
                if (entry.getKey() != null && delta != 0L) {
                    entries.put(entry.getKey(), delta);
                }
            }
            return new Batch(batchId, entries);
        } catch (Exception e) {
            log.warn("读取计数缓冲失败: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /** 容忍脏值：解析不出来一律当 0（脏 field 会在落库时被丢弃，不会卡住整批） */
    private static long parse(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
