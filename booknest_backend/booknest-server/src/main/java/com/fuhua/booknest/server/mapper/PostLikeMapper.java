package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostLikeMapper {
    /**
     * 插入帖子点赞记录
     * @param postLike 点赞记录信息
     */
    void insert(PostLike postLike);

    /**
     * 根据帖子ID与用户ID删除点赞记录
     *
     * @return 实际删除的行数（0 表示本来就没点过赞；并发场景下用它判断
     *         「这次删除是不是真的生效了」，从而决定要不要扣减计数）
     */
    int deleteByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

    /**
     * 根据帖子ID删除该帖下所有点赞记录（级联删除帖子时调用）
     * @param postId 帖子ID
     */
    void deleteByPostId(@Param("postId") String postId);

    /**
     * 根据帖子ID与用户ID查询点赞记录
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return 点赞记录（不存在返回 null）
     */
    PostLike selectByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

    /**
     * 统计帖子点赞数
     * @param postId 帖子ID
     * @return 点赞数
     */
    int countByPostId(@Param("postId") String postId);

    /**
     * 查询用户点赞过的全部帖子 ID。
     *
     * <p>用途：Redis 状态缓存冷启动 —— 用户第一次访问列表时一次性把「他赞过哪些帖子」
     * 拉进缓存，之后靠写路径增量维护。单个用户的点赞量有限（几百到几千），
     * 一次全量拉取可以接受。</p>
     *
     * @param userId 用户ID
     * @return 帖子 ID 列表
     */
    List<String> listLikedPostIdsByUser(@Param("userId") String userId);
}
