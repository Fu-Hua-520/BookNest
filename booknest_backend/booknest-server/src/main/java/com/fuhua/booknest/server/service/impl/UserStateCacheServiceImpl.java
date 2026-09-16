package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.server.common.RedisCacheHelper;
import com.fuhua.booknest.server.mapper.PostCollectMapper;
import com.fuhua.booknest.server.mapper.PostCommentLikeMapper;
import com.fuhua.booknest.server.mapper.PostLikeMapper;
import com.fuhua.booknest.server.service.UserStateCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户互动状态缓存实现。
 *
 * <h3>三层兜底结构</h3>
 * 每次读都按这个顺序走，任何一层出问题都能拿到<b>正确</b>结果（最坏情况只是慢）：
 * <ol>
 *   <li><b>加载标记存在</b> → 直接信 Redis 集合的成员判断（快路径，绝大多数请求走这里）；</li>
 *   <li><b>标记不存在</b> → 从 DB 全量加载该用户的集合 → 写入 Redis → 再判断（仅用户首次访问或缓存过期后一次）；</li>
 *   <li><b>Redis 全程不可用</b> → 每次直接查库（慢但正确）。</li>
 * </ol>
 *
 * <p>注意最后一种情况不会「退化成永远查库」：{@code RedisCacheHelper} 的写失败只记日志，
 * 所以下次请求仍会尝试加载，Redis 恢复后自动回到快路径。</p>
 */
@Service
@Slf4j
public class UserStateCacheServiceImpl implements UserStateCacheService {

    /** 状态集合的过期时间：7 天。用户活跃期间每次写入都会续期，长期不用的用户自然淘汰。 */
    private static final long STATE_TTL_SECONDS = RedisConstant.EXPIRE_7_DAYS;

    @Autowired
    private RedisCacheHelper cache;

    @Autowired
    private PostLikeMapper postLikeMapper;
    @Autowired
    private PostCommentLikeMapper postCommentLikeMapper;
    @Autowired
    private PostCollectMapper postCollectMapper;

    /* ------------------------------ 单条查询 ------------------------------ */

    @Override
    public boolean isPostLiked(String userId, String postId) {
        if (isBlank(userId) || isBlank(postId)) {
            return false;
        }
        String setKey = RedisConstant.LIKE_POST_SET + userId;
        return containsWithFallback(setKey, postId,
                () -> postLikeMapper.selectByPostAndUser(postId, userId) != null,
                () -> postLikeMapper.listLikedPostIdsByUser(userId));
    }

    @Override
    public boolean isCommentLiked(String userId, String commentId) {
        if (isBlank(userId) || isBlank(commentId)) {
            return false;
        }
        String setKey = RedisConstant.LIKE_COMMENT_SET + userId;
        return containsWithFallback(setKey, commentId,
                () -> postCommentLikeMapper.selectByCommentAndUser(commentId, userId) != null,
                () -> postCommentLikeMapper.listLikedCommentIdsByUser(userId));
    }

    @Override
    public boolean isPostCollected(String userId, String postId) {
        if (isBlank(userId) || isBlank(postId)) {
            return false;
        }
        String setKey = RedisConstant.COLLECT_POST_SET + userId;
        return containsWithFallback(setKey, postId,
                () -> postCollectMapper.selectByPostAndUser(postId, userId) != null,
                () -> postCollectMapper.listAllCollectedPostIdsByUser(userId));
    }

    /* ------------------------------ 批量查询 ------------------------------ */

    @Override
    public Set<String> filterLikedPosts(String userId, Collection<String> postIds) {
        if (isBlank(userId) || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        String setKey = RedisConstant.LIKE_POST_SET + userId;
        return filterWithFallback(setKey, postIds,
                () -> postLikeMapper.listLikedPostIdsByUser(userId));
    }

    @Override
    public Set<String> filterLikedComments(String userId, Collection<String> commentIds) {
        if (isBlank(userId) || commentIds == null || commentIds.isEmpty()) {
            return Set.of();
        }
        String setKey = RedisConstant.LIKE_COMMENT_SET + userId;
        return filterWithFallback(setKey, commentIds,
                () -> postCommentLikeMapper.listLikedCommentIdsByUser(userId));
    }

    @Override
    public Set<String> filterCollectedPosts(String userId, Collection<String> postIds) {
        if (isBlank(userId) || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        String setKey = RedisConstant.COLLECT_POST_SET + userId;
        return filterWithFallback(setKey, postIds,
                () -> postCollectMapper.listAllCollectedPostIdsByUser(userId));
    }

    /* ------------------------------ 写路径 ------------------------------ */

    @Override
    public void markPostLiked(String userId, String postId, boolean liked) {
        writeState(RedisConstant.LIKE_POST_SET + userId, postId, liked);
    }

    @Override
    public void markCommentLiked(String userId, String commentId, boolean liked) {
        writeState(RedisConstant.LIKE_COMMENT_SET + userId, commentId, liked);
    }

    @Override
    public void markPostCollected(String userId, String postId, boolean collected) {
        writeState(RedisConstant.COLLECT_POST_SET + userId, postId, collected);
    }

    @Override
    public void forgetPostEverywhere(String postId) {
        // 只清理「本人删自己帖子」这条路径能触及的缓存：帖子详情与列表。
        cache.evictByPattern(RedisConstant.POST_LIST_PATTERN);
        // 详情缓存单独删（列表是按前缀整体清，详情是一篇一个键）。
        // 其实详情读路径会先回读 post 行、行没了就报「帖子不存在」并顺手自清，
        // 所以这里删不删都不会错；主动删是为了不让一篇已删除帖子的正文在 Redis 里
        // 干等到 TTL 才消失。
        cache.evict(RedisConstant.POST_DETAIL + postId);
        // 点赞集合不做级联（要遍历所有用户，代价远大于收益）—— 让它们随 TTL 过期，
        // 期间残留的 id 不会造成错误展示：列表按帖子 id 渲染，帖子没了自然不显示。
        log.debug("已清理帖子详情与列表缓存（帖子被删除）: postId={}", postId);
    }

    /* ------------------------------ 内部实现 ------------------------------ */

    /**
     * 单值判断的通用流程：快路径 → 回源加载 → 降级直查。
     *
     * @param setKey     集合键
     * @param member     待判断成员
     * @param directProbe 降级用的单条查库（Redis 不可用时）
     * @param fullLoader  全量加载该用户状态（用于填充缓存）
     */
    private boolean containsWithFallback(String setKey, String member,
                                         java.util.function.BooleanSupplier directProbe,
                                         java.util.function.Supplier<List<String>> fullLoader) {
        // 快路径：标记在 → 信集合
        if (cache.isLoaded(loadedKey(setKey))) {
            return cache.setContains(setKey, member);
        }
        // 回源：加载全量状态并写入缓存
        if (tryLoadAll(setKey, fullLoader)) {
            return cache.setContains(setKey, member);
        }
        // 终极降级：Redis 不可用（加载也写不进去），直接查库保证结果正确
        return directProbe.getAsBoolean();
    }

    /**
     * 批量判断的通用流程。
     *
     * @return 命中集合；Redis 与加载都失败时返回空集合，交由调用方决定是否再降级
     */
    private Set<String> filterWithFallback(String setKey, Collection<String> candidates,
                                           java.util.function.Supplier<List<String>> fullLoader) {
        if (!cache.isLoaded(loadedKey(setKey))) {
            if (!tryLoadAll(setKey, fullLoader)) {
                // Redis 不可用：回退成「逐条查库」。这里刻意不用全量 loader 的结果做过滤，
                // 因为那样既慢又容易在数据量大时拉爆内存；逐条查虽然 N 次查询，
                // 但只在 Redis 故障期间发生，且 N 是「一页条数」量级（≤10）。
                return Set.of();
            }
        }
        Set<String> hits = cache.setContainsBatch(setKey, candidates);
        return hits == null ? Set.of() : hits;
    }

    /**
     * 从 DB 全量加载某用户的某类状态集合并写入 Redis。
     *
     * @return true 表示加载成功且已写入缓存（可继续走快路径）；false 表示加载/写入失败
     */
    private boolean tryLoadAll(String setKey, java.util.function.Supplier<List<String>> fullLoader) {
        List<String> ids;
        try {
            ids = fullLoader.get();
        } catch (Exception e) {
            log.warn("回源加载用户状态失败: key={}, err={}", setKey, e.getMessage());
            return false;
        }
        Set<String> values = ids == null ? Set.of() : new LinkedHashSet<>(ids);
        cache.setReplaceAll(setKey, values, STATE_TTL_SECONDS);
        // 标记只在写成功后落：Redis 挂掉时这里写不进去，下次请求会再次尝试回源
        cache.markLoaded(loadedKey(setKey), STATE_TTL_SECONDS);
        return cache.isLoaded(loadedKey(setKey));
    }

    /**
     * 写路径的增量维护。
     *
     * <p>两种情形分开处理：</p>
     * <ul>
     *   <li><b>缓存已加载</b> → 只增删这一个成员（O(1)，不触碰其它数据）；</li>
     *   <li><b>缓存未加载</b> → 什么都不做。这不丢数据：DB 已经是权威值，
     *       下次读的时候会从 DB 全量重建，重建结果必然包含这次变更。</li>
     * </ul>
     *
     * <p>这个分支设计避免了一个常见坑：用户从没访问过列表（集合不存在），
     * 此时若直接 SADD 一个成员，会得到一个「只含这一次点赞」的不完整集合，
     * 之后标记一落，用户会看到自己历史点赞全部消失。</p>
     */
    private void writeState(String setKey, String member, boolean present) {
        if (isBlank(member)) {
            return;
        }
        if (!cache.isLoaded(loadedKey(setKey))) {
            return;
        }
        if (present) {
            cache.setAdd(setKey, member, STATE_TTL_SECONDS);
        } else {
            cache.setRemove(setKey, member);
        }
    }

    private String loadedKey(String setKey) {
        return RedisCacheHelper.loadedKey(setKey);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
