package com.fuhua.booknest.server.service;

import java.util.Collection;
import java.util.Set;

/**
 * 用户互动状态缓存（点赞 / 收藏）。
 *
 * <p>为什么单独抽一层：这类状态有三个绕不开的难点，集中在这里处理，业务代码就不用各写一遍。</p>
 *
 * <h3>难点一：不能缓存「查无此人」</h3>
 * 帖子列表要给每条帖子标「我点没点过赞」。若每次都查库，一页 10 条就是 10 次查询；
 * 但若直接缓存一个「命中即 true、未命中即 false」的集合，那么<b>集合本身不存在时</b>
 * （Redis 重启、key 过期、缓存清理）会被误读成「都没点过赞」——
 * 用户会看到自己点过的赞突然全没了。所以每个用户额外带一个「已从 DB 加载」的标记键，
 * 没有标记就必须回源，绝不把「未知」当「否」。
 *
 * <h3>难点二：写路径必须同步维护</h3>
 * 点赞/取消点赞后立刻更新缓存，否则列表页会显示旧状态。
 *
 * <h3>难点三：Redis 挂了不能影响功能</h3>
 * 这里全部方法都 fail-open：Redis 异常 → 回退查库 → 返回正确结果（只是慢一点）。
 * 任何时候都不允许因为 Redis 出问题而让点赞状态显示错误或接口报错。
 */
public interface UserStateCacheService {

    /* ------------------------------ 单条查询 ------------------------------ */

    /** 当前用户是否点赞了该帖子（未登录返回 false） */
    boolean isPostLiked(String userId, String postId);

    /** 当前用户是否点赞了该评论 */
    boolean isCommentLiked(String userId, String commentId);

    /** 当前用户是否收藏了该帖子 */
    boolean isPostCollected(String userId, String postId);

    /* ------------------------------ 批量查询（列表页用） ------------------------------ */
    //
    // ⚠️ 现状说明（2026-09-14）：这三个批量方法目前只有 filterLikedComments 真正被调用
    // （PostInteractionServiceImpl.prefetchForComments 组装评论区时用）。
    // filterLikedPosts / filterCollectedPosts 暂时没有调用方，原因是：
    //   PostVO / PostDetailVO 里根本没有「当前用户是否点赞/收藏」字段 ——
    //   前端是在打开详情页时另外调 /post/{id}/like/status 与 /post/{id}/collect/status 拿的。
    // 保留它们不是「忘了删」，而是因为一旦将来把喜欢态内联进列表 VO（那样能省掉前端的
    // N 次状态请求），这两个方法就是现成的入口。在此之前它们属于预留接口，非死代码清理对象。

    /**
     * 批量判断这批帖子里当前用户点赞了哪些。
     *
     * @return 已点赞的 postId 子集；userId 为空时返回空集合
     */
    Set<String> filterLikedPosts(String userId, Collection<String> postIds);

    /** 批量判断这批评论里当前用户点赞了哪些（评论区组装用，已接入） */
    Set<String> filterLikedComments(String userId, Collection<String> commentIds);

    /** 批量判断这批帖子里当前用户收藏了哪些 */
    Set<String> filterCollectedPosts(String userId, Collection<String> postIds);

    /* ------------------------------ 写路径维护 ------------------------------ */

    /**
     * 点赞状态变更后同步缓存。
     *
     * <p>刻意用「加成员 / 删成员」的增量更新，而不是「直接删掉整个集合」：
     * 删集合会导致下一次列表请求重新全量回源，而点赞是高频动作，
     * 每点一次就触发一次全量回源很不划算。</p>
     *
     * @param liked true 表示刚刚点赞，false 表示刚刚取消
     */
    void markPostLiked(String userId, String postId, boolean liked);

    void markCommentLiked(String userId, String commentId, boolean liked);

    void markPostCollected(String userId, String postId, boolean collected);

    /**
     * 该用户的帖子被删除后，把它的 id 从所有相关用户的缓存里摘掉。
     *
     * <p>仅用于「用户自己删自己的帖子」这种能确定影响范围且量很小的场景。
     * 大批量数据变更（如管理员删帖）不做缓存级联，让 TTL 自然过期。</p>
     */
    void forgetPostEverywhere(String postId);
}
