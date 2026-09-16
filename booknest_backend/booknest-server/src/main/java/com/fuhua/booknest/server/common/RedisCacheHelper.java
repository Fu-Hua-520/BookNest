package com.fuhua.booknest.server.common;

import com.fuhua.booknest.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存工具门面 —— 所有 Redis 读写都从这里走。
 *
 * <p><b>核心设计：全部 fail-open。</b>
 * Redis 在本项目里是纯粹的「性能加速层」，不是事实来源 —— 标签、书吧、列表的真相
 * 始终在 MySQL 里。所以任何一次 Redis 异常（连不上、超时、序列化失败）都必须降级成
 * 「当作缓存未命中」，让调用方回源查库，而<b>绝不能把异常抛给业务</b>。
 * 这与 {@code CommonController.checkUploadRate} 里「限流抖动就放行」是同一套哲学。</p>
 *
 * <p><b>空值不缓存。</b>查询结果为空时不写 Redis —— 不引入空值占位符那一套，
 * 免得「缓存了一个空列表、结果新数据一直读不到」这类问题。空结果回源的代价可以接受。</p>
 */
@Component
@Slf4j
public class RedisCacheHelper {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /* ------------------------------ 对象缓存 ------------------------------ */

    /**
     * 读缓存：命中返回对象，未命中或 Redis 异常返回 null。
     *
     * @param key 缓存键
     * @param <T> 期望返回类型（调用方自行保证与写入时一致）
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        try {
            return (T) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("读缓存失败，按未命中处理: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 写缓存（带过期时间）。失败只记日志。
     */
    public void set(String key, Object value, long expireSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写缓存失败，忽略: key={}, err={}", key, e.getMessage());
        }
    }

    /** 删除单个键 */
    public void evict(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("删除缓存失败，忽略: key={}, err={}", key, e.getMessage());
        }
    }

    /* ------------------------------ 集合（状态位） ------------------------------ */

    /**
     * 判断集合成员是否存在（空集合时必然 false）。
     *
     * @param key    集合键
     * @param member 成员
     */
    public boolean setContains(String key, String member) {
        try {
            Boolean hit = redisTemplate.opsForSet().isMember(key, member);
            return Boolean.TRUE.equals(hit);
        } catch (Exception e) {
            log.warn("查询集合成员失败，按不存在处理: key={}, err={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 批量判断成员是否存在，用于「一页列表一次性判断当前用户的点赞状态」。
     *
     * <p>用 pipeline 打包 N 个 SISMEMBER，把 N 次网络往返压成 1 次。
     * 任一子命令失败都不影响其它结果（失败位按「不存在」处理）。</p>
     *
     * @param key     集合键
     * @param members 待判断成员
     * @return 命中的成员子集（永远非 null，便于调用方直接 contains 判断）
     */
    public Set<String> setContainsBatch(String key, Collection<String> members) {
        Set<String> hits = new LinkedHashSet<>();
        if (members == null || members.isEmpty()) {
            return hits;
        }
        try {
            List<Object> results = redisTemplate.executePipelined(
                    (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                        var rawKey = redisTemplate.getStringSerializer().serialize(key);
                        for (String member : members) {
                            if (member == null) {
                                continue;
                            }
                            connection.setCommands().sIsMember(rawKey,
                                    redisTemplate.getStringSerializer().serialize(member));
                        }
                        return null;
                    });
            int idx = 0;
            List<String> ordered = new ArrayList<>();
            for (String member : members) {
                if (member != null) {
                    ordered.add(member);
                }
            }
            for (Object result : results) {
                if (idx >= ordered.size()) {
                    break;
                }
                if (Boolean.TRUE.equals(result)) {
                    hits.add(ordered.get(idx));
                }
                idx++;
            }
        } catch (Exception e) {
            log.warn("批量查询集合成员失败，全部按不存在处理: key={}, err={}", key, e.getMessage());
        }
        return hits;
    }

    /** 向集合追加成员（可同时设置整个集合的过期时间） */
    public void setAdd(String key, String member, long expireSeconds) {
        if (member == null) {
            return;
        }
        try {
            redisTemplate.opsForSet().add(key, member);
            // 每次写都续期：活跃用户的缓存不该因为「首次加载过去很久了」而失效
            redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入集合成员失败，忽略: key={}, err={}", key, e.getMessage());
        }
    }

    /** 从集合移除成员 */
    public void setRemove(String key, String member) {
        if (member == null) {
            return;
        }
        try {
            redisTemplate.opsForSet().remove(key, member);
        } catch (Exception e) {
            log.warn("移除集合成员失败，忽略: key={}, err={}", key, e.getMessage());
        }
    }

    /** 整体覆盖地写入一个集合（用于首次从 DB 加载全量状态） */
    public void setReplaceAll(String key, Collection<String> members, long expireSeconds) {
        try {
            redisTemplate.delete(key);
            if (members != null && !members.isEmpty()) {
                redisTemplate.opsForSet().add(key, members.toArray());
            }
            redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("整体写入集合失败，忽略: key={}, err={}", key, e.getMessage());
        }
    }

    /** 读取整个集合（用于回源比对；异常时返回 null 以便调用方区分「空集合」与「读失败」） */
    @SuppressWarnings("unchecked")
    public Set<String> setMembers(String key) {
        try {
            Set<Object> raw = redisTemplate.opsForSet().members(key);
            if (raw == null) {
                return Set.of();
            }
            Set<String> result = new LinkedHashSet<>(raw.size());
            for (Object item : raw) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("读取集合失败: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /* ------------------------------ 加载标记 ------------------------------ */

    /**
     * 「这个用户的点赞集合已经从 DB 全量加载过」的标记。
     *
     * <p>为什么不直接判断「集合非空」：一个用户完全可能一条都没赞过，
     * 这时集合是空的，用「空即未加载」会导致每次请求都回源重查 ——
     * 正是我们想避免的事情。所以单独放一个短 TTL 的标记键。</p>
     */
    public boolean isLoaded(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            return false;
        }
    }

    /** 打上「已加载」标记 */
    public void markLoaded(String key, long expireSeconds) {
        set(key, "1", expireSeconds);
    }

    /** 清除「已加载」标记（下次访问时强制回源重建） */
    public void clearLoaded(String key) {
        evict(key);
    }

    /* ------------------------------ 按前缀清理 ------------------------------ */

    /**
     * 按前缀清理缓存（用 SCAN 而不是 KEYS：KEYS 在大 key 空间下会阻塞 Redis 单线程）。
     *
     * <p>注意：本项目只用 0 号库，且键空间很小（都是 bn:cache: 前缀下的业务缓存），
     * SCAN 的轻微非原子性不构成问题。</p>
     *
     * @param pattern 匹配模式，例如 {@code bn:cache:post:list:*}
     * @return 实际删除的键数量；Redis 异常时返回 -1
     */
    public long evictByPattern(String pattern) {
        long deleted = 0;
        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                connection -> connection.keyCommands().scan(
                        ScanOptions.scanOptions().match(pattern).count(200).build()))) {
            if (cursor == null) {
                return 0;
            }
            List<String> batch = new ArrayList<>(200);
            while (cursor.hasNext()) {
                String key = (String) redisTemplate.getKeySerializer().deserialize(cursor.next());
                if (key != null) {
                    batch.add(key);
                }
                if (batch.size() >= 200) {
                    deleted += redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                deleted += redisTemplate.delete(batch);
            }
        } catch (Exception e) {
            log.warn("按前缀清理缓存失败: pattern={}, err={}", pattern, e.getMessage());
            return -1;
        }
        return deleted;
    }

    /* ------------------------------ 缓存穿透辅助 ------------------------------ */

    /**
     * 「先查缓存，未命中则回源并写入」的标准模板。
     *
     * @param key           缓存键
     * @param expireSeconds 过期时间
     * @param loader        回源逻辑（查库）
     * @param <T>           返回类型
     * @return 缓存或回源结果；loader 返回 null / 空集合时不写缓存
     */
    public <T> T getOrLoad(String key, long expireSeconds, Supplier<T> loader) {
        T cached = get(key);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (shouldCache(loaded)) {
            set(key, loaded, expireSeconds);
        }
        return loaded;
    }

    /** 空集合 / 空列表不缓存，避免把「暂时没有」固化下来 */
    private boolean shouldCache(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    /**
     * 缓存键的统一拼装入口，避免各处手写前缀拼错。
     *
     * <p><b>null 的编码约定：</b>null 片段统一编码成字面量 {@code all}，
     * 用于表达「该筛选维度不限制」。这带来一个理论上的键碰撞：
     * 若某个业务ID真的等于字符串 {@code "all"}，它的键会和「不限制」重合。
     * 本项目所有业务ID都是 UUID（category / tag / post / user 均如此），
     * 不存在等于 {@code "all"} 的ID，故该碰撞不可达。</p>
     */
    public static String key(String prefix, Object... parts) {
        StringBuilder sb = new StringBuilder(prefix);
        for (Object part : parts) {
            sb.append(part == null ? "all" : part).append(':');
        }
        // 去掉末尾冒号，让键更好看也更短
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /** 集合类缓存的「已加载」标记键 */
    public static String loadedKey(String setKey) {
        return RedisConstant.CACHE_PREFIX + "loaded:" + setKey;
    }
}
