package com.fuhua.booknest.server.job;

import com.fuhua.booknest.server.common.CountBuffer;
import com.fuhua.booknest.server.mapper.CountFlushLedgerMapper;
import com.fuhua.booknest.server.mapper.PostCommentMapper;
import com.fuhua.booknest.server.mapper.PostMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 计数落库任务 —— 把 {@link CountBuffer} 里攒下的增量批量写进 MySQL。
 *
 * <h3>这是项目里唯一的定时任务</h3>
 * 之前全项目零 {@code @Scheduled}，是因为当时所有计数都直写 MySQL、不存在「待落库」状态。
 * 现在浏览 / 点赞 / 评论 / 收藏的计数先在 Redis 累积，就必须有人负责把它们送回去 ——
 * 这个任务就是那唯一的新增。**计数之外的任何东西都不该往这里加**：
 * 一旦这里承担了业务逻辑，它就变成了「挂了没人发现的隐藏写入路径」。
 *
 * <h3>一轮执行的三步</h3>
 * <ol>
 *   <li><b>先处理残留</b>：上一轮若崩在「已换出、未落库」之间，{@code flushing} 里还留着那批数据，
 *       必须先落掉。否则下一步的 RENAME 会把它直接覆盖掉 —— 静默丢计数。
 *       判断「有没有残留」必须用 {@link CountBuffer#hasLeftover()}：残留里可能全是净零增量
 *       （同一窗口内 +1 又 -1），那时读出来是空的，但残留键确实还在；</li>
 *   <li><b>原子换出</b>：RENAME pending → flushing。换出后新来的写入落进新的空 HASH，
 *       与本批互不干扰（这正是它比「读出来再逐条删」可靠的地方）；</li>
 *   <li><b>落库并确认</b>：写成功才 DEL {@code flushing}；写失败就把数据留在那儿，
 *       下一轮当残留重试 —— <b>宁可多跑一次幂等账本，不可丢失</b>。</li>
 * </ol>
 *
 * <h3>幂等：为什么「重落一次」不会重复累加</h3>
 * 每批增量带一个批次ID（写在缓冲 HASH 的 {@code __batch} 字段里，
 * 由 RENAME 与增量原子地一起搬进 {@code flushing}，所以残留永远自带身份）。
 * 落库时先在 {@code count_flush_ledger} 里按这个ID 登记，
 * <b>而登记与计数 UPDATE 在同一个事务里</b>：
 * <pre>
 *   这个ID 已存在 → 说明本批已经写过了（上次写库成功、但确认删除失败留下的残留）→ 只确认，不再碰计数列
 *   这个ID 不存在 → 正常落库：先 UPDATE 计数，再 INSERT 这个ID
 * </pre>
 * 于是「写库成功但确认删除失败」这条唯一会导致重复累加的路被彻底关掉。
 *
 * <h3>为什么「已经落库成功」的批次还要再被处理一次</h3>
 * COMMIT 与 {@code DEL flushing} 是两个无法合并的动作，中间那段窗口里出任何意外
 * （进程被 kill、网络抖动让删除失败），残留就会留到下一轮。而下一轮任务
 * <b>无法从 Redis 分辨这批到底是「上次没落上」还是「上次落了但没收尾」</b> ——
 * 两种情况在它眼里一模一样。所以它只能重走一遍完整流程，由账本给出判定：
 * 命中就跳过计数列、只把 {@code flushing} 删掉。
 * <b>要认清的是：「重试」的目标不是再写一次 MySQL，而是让残留键消失。</b>
 * 写不写计数列由账本决定，而删掉 flushing 必须做 —— 不收尾的话，
 * {@link CountBuffer#drain()} 见到残留就不换出新批次，残留会把后续所有批次永远挡在门外。
 * 这个窗口无法消除（跨两个存储不存在第三个原子点），账本能做的只是让「重走一遍」变安全。
 *
 * 账本必须落在 MySQL 而不是 Redis：登记与写库必须原子，放到 Redis 里就又变成两个存储的
 * 两阶段提交 —— 登记成功而写库失败会丢计数，写库成功而登记失败会重复计数。
 *
 * <h3>仍然存在的三个代价（如实列出）</h3>
 * <ul>
 *   <li><b>落库瞬间的可见性空档。</b>换出之后、写库完成之前（毫秒级），
 *       数据库还没更新而缓冲已经空了，此刻读到的计数会少算这一批。
 *       下一次读就恢复正常。这是「缓冲 + 批量落库」固有的代价，无法在不加锁的前提下消除；</li>
 *   <li><b>残留若没有身份，幂等就失效。</b>只可能发生在「本次升级之前就已经躺在 flushing 里的残留」
 *       或人为改键之后 —— 那时无法判断这批是否已落过库，只能按新批次落（可能重复一次），
 *       日志里会明确告警。正常流程下批次身份与增量是原子搬移的，不会缺；</li>
 *   <li><b>Redis 真丢数据就是永久少算。</b>{@code docker-compose} 里开了
 *       {@code --appendonly yes}，窗口是秒级，但存在。</li>
 * </ul>
 *
 * <h3>为什么不给失败加重试次数上限</h3>
 * 落库失败几乎只有一种原因：MySQL 暂时不可用。此时正确的动作是「等它好起来」，
 * 而不是重试到放弃 —— 放弃就等于把这批计数丢了。所以失败一律保留、下轮再试。
 */
@Component
@Slf4j
public class CountFlushJob {

    /* post 四个增量在数组里的下标（顺序与 PostMapper.applyCountDeltas 的参数一致） */
    private static final int VIEW = 0;
    private static final int LIKE = 1;
    private static final int COMMENT = 2;
    private static final int COLLECT = 3;

    /**
     * 幂等账本记录的保留天数。
     *
     * <p>登记只在「残留可能被重落」的窗口里有意义，而这个窗口以落库周期计（默认 30 秒）。
     * 7 天是极大的余量：真有一条残留躺了 7 天，说明落库早就停摆，那时问题也不在账本上。
     * 代价可以忽略 —— 每轮最多一条记录，7 天也就两万行。</p>
     */
    private static final int LEDGER_RETAIN_DAYS = 7;

    @Autowired
    private CountBuffer countBuffer;
    @Autowired
    private PostMapper postMapper;
    @Autowired
    private PostCommentMapper postCommentMapper;
    @Autowired
    private CountFlushLedgerMapper countFlushLedgerMapper;
    @Autowired
    private PlatformTransactionManager transactionManager;

    /**
     * 显式事务模板。
     *
     * <p><b>为什么不用 {@code @Transactional}：</b>本类没有任何被外部代理调用的入口
     * （Spring 只调度到 {@code flushCounts} 自己），而 {@code writeToDb} 是被同类方法直接调用的
     * ——自调用不走代理，注解会被静默忽略。用模板显式圈出边界，既不依赖代理也一眼可辨。
     * 与 {@code PostServiceImpl.txTemplate} 是同一套写法。</p>
     */
    private TransactionTemplate txTemplate;

    @PostConstruct
    void initTxTemplate() {
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 把缓冲里的计数增量批量落库。
     *
     * <p>用 {@code fixedDelay} 而不是 {@code fixedRate}：间隔从上一次执行<b>结束</b>开始算，
     * 因此网络抖动导致某轮变慢时不会堆积起并发执行。</p>
     *
     * <p>间隔由 {@code booknest.count-buffer.flush-interval-ms} 控制（默认 30 秒）。
     * 调小能让计数更实时、但 UPDATE 更碎；调大则相反。</p>
     */
    @Scheduled(fixedDelayString = "${booknest.count-buffer.flush-interval-ms:30000}")
    public void flushCounts() {
        if (!countBuffer.tryAcquireFlushLock()) {
            return;
        }
        try {
            // ① 上一轮的残留优先（有残留时 drain() 不会换出新批次，所以必须先把它处理掉）
            if (countBuffer.hasLeftover()) {
                CountBuffer.Batch carried = countBuffer.leftover();
                if (carried == null) {
                    // 读失败 ≠ 没有残留：这里绝不能顺手确认删除，否则就是真的丢计数
                    log.warn("残留计数读不出来，本轮放弃（保留残留，下一轮重试）");
                    return;
                }
                if (!carried.isEmpty() && !writeToDb(carried)) {
                    log.warn("残留计数落库失败，本轮放弃（下一轮重试）: 条目数={}", carried.size());
                    return;
                }
                // 残留里已无净增量时也要确认掉，否则它会永久占着残留位、把后续批次全挡在门外
                countBuffer.confirmDrained();
            }

            // ② 换出本批（净零批次已由 drain() 内部就地确认丢弃，这里只会拿到「有东西可落」的批）
            CountBuffer.Batch drained = countBuffer.drain();
            if (drained == null || drained.isEmpty()) {
                return;
            }

            // ③ 落库 + 确认
            if (writeToDb(drained)) {
                countBuffer.confirmDrained();
                log.debug("计数落库完成: batchId={}, 条目数={}", drained.batchId(), drained.size());
            } else {
                log.warn("计数落库失败，数据已保留在缓冲中等待下一轮重试: batchId={}, 条目数={}",
                        drained.batchId(), drained.size());
            }
        } catch (Exception e) {
            // 兜底：任何意外都不该让调度线程带着异常退出（否则任务可能被取消）
            log.error("计数落库任务异常", e);
        } finally {
            countBuffer.releaseFlushLock();
        }
    }

    /**
     * 把一批缓冲条目写进 MySQL。
     *
     * <p>先把条目按实体归拢：同一篇帖子的四个增量合成<b>一条</b> UPDATE
     * （{@code view_count = view_count + ?, like_count = like_count + ? ...}），
     * 评论点赞各自一条 UPDATE。这样「一条帖子被浏览 300 次 + 点赞 20 次」
     * 只产生 1 条 UPDATE，而不是 320 条。</p>
     *
     * <p>整批放在一个事务里，并带上幂等登记（见类注释）：要么全落、要么全不落，
     * 而且「落过了」这件事和「落了什么」在同一个事务里定下来。</p>
     *
     * @param batch 一批增量（含批次身份）
     * @return 落库成功返回 true；失败返回 false（调用方不要调用 confirmDrained，留待重试）
     */
    private boolean writeToDb(CountBuffer.Batch batch) {
        // 帖子：ID → [view, like, comment, collect]
        Map<String, long[]> postDeltas = new LinkedHashMap<>();
        // 评论点赞：ID → 增量
        Map<String, Long> commentDeltas = new LinkedHashMap<>();
        int unrecognized = 0;

        for (Map.Entry<String, Long> entry : batch.entries().entrySet()) {
            CountBuffer.CounterType type = CountBuffer.typeOf(entry.getKey());
            String id = CountBuffer.idOf(entry.getKey());
            long delta = entry.getValue() == null ? 0L : entry.getValue();
            if (type == null || id == null) {
                // 认不出来就跳过，但不表示「落掉了」——整批仍会被确认丢弃，
                // 所以这里必须告警，否则这类脏 field 会无声消失
                unrecognized++;
                continue;
            }
            if (delta == 0L) {
                continue;
            }
            switch (type) {
                case POST_VIEW -> postDeltas.computeIfAbsent(id, k -> new long[4])[VIEW] += delta;
                case POST_LIKE -> postDeltas.computeIfAbsent(id, k -> new long[4])[LIKE] += delta;
                case POST_COMMENT -> postDeltas.computeIfAbsent(id, k -> new long[4])[COMMENT] += delta;
                case POST_COLLECT -> postDeltas.computeIfAbsent(id, k -> new long[4])[COLLECT] += delta;
                case COMMENT_LIKE -> commentDeltas.merge(id, delta, Long::sum);
            }
        }

        if (unrecognized > 0) {
            log.warn("计数缓冲里有 {} 个无法识别的条目（疑似枚举改名或键空间被污染），已跳过", unrecognized);
        }
        if (postDeltas.isEmpty() && commentDeltas.isEmpty()) {
            // 没有任何可写的东西（例如整批都是认不出的 field）：直接当成功，让调用方确认丢弃。
            // 这里刻意不登记账本 —— 没写任何计数列，登记它没有意义。
            return true;
        }

        // 批次身份正常一定在（它随 RENAME 与增量一起被搬到 flushing）。缺失只有两种可能：
        // 本次升级之前就已经躺在暂存区的残留，或人工改过键。两种情况下「这批是否已落过库」
        // 都无从判断，只能按新批次落 —— 重复一次，好过丢掉一批。
        String batchId = batch.batchId();
        if (batchId == null || batchId.isBlank()) {
            batchId = UUID.randomUUID().toString();
            log.warn("残留批次没有身份（升级前遗留的残留或人工改动），按新批次落库: 新 batchId={}, 条目数={}",
                    batchId, batch.entries().size());
        }
        final String ledgerId = batchId;

        try {
            txTemplate.executeWithoutResult(status -> {
                if (countFlushLedgerMapper.countBatch(ledgerId) > 0) {
                    // 幂等账本要挡的就是这一种：上次「写库成功、但确认删除失败」留下的残留。
                    // 只做确认（调用方随后 DEL flushing），不再碰计数列。
                    log.warn("批次 {} 此前已落过库（上次写库成功后确认失败），本次跳过，不再重复累加", ledgerId);
                    return;
                }
                for (Map.Entry<String, long[]> entry : postDeltas.entrySet()) {
                    long[] d = entry.getValue();
                    postMapper.applyCountDeltas(entry.getKey(), d[VIEW], d[LIKE], d[COMMENT], d[COLLECT]);
                }
                for (Map.Entry<String, Long> entry : commentDeltas.entrySet()) {
                    postCommentMapper.applyLikeCountDelta(entry.getKey(), entry.getValue());
                }
                // 登记必须与上面的 UPDATE 同事务：只有这样「账本里有这个ID」才等价于「计数已落库」。
                // 若这里撞主键（只可能是多实例并发落同一批）会抛异常 → 整批回滚 → 下一轮重试，正确。
                countFlushLedgerMapper.insert(ledgerId);
                // 顺带清理过期登记。放在这里而不是单独的定时任务：本项目只有这一个调度入口，
                // 而账本只在「有批次落库」时才增长，跟着落库清最自然。
                countFlushLedgerMapper.deleteAppliedBeforeDays(LEDGER_RETAIN_DAYS);
            });
            return true;
        } catch (Exception e) {
            log.warn("写库失败: batchId={}, 帖子数={}, 评论数={}, err={}",
                    ledgerId, postDeltas.size(), commentDeltas.size(), e.getMessage());
            return false;
        }
    }
}
