package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostCollect;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostCollectMapper {

    /**
     * 插入帖子收藏记录
     * @param postCollect 收藏记录信息
     */
    void insert(PostCollect postCollect);

    /**
     * 根据帖子ID与用户ID删除收藏记录
     *
     * @return 实际删除的行数（0 表示本来就没收藏过；并发下用它决定是否扣减计数）
     */
    int deleteByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

    /**
     * 根据帖子ID删除该帖下所有收藏记录（级联删除帖子时调用）
     * @param postId 帖子ID
     */
    void deleteByPostId(@Param("postId") String postId);

    /**
     * 根据帖子ID与用户ID查询收藏记录
     * @param postId 帖子ID
     * @param userId 用户ID
     * @return 收藏记录（不存在返回 null）
     */
    PostCollect selectByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

    /**
     * 统计帖子收藏数
     * @param postId 帖子ID
     * @return 收藏数
     */
    int countByPostId(@Param("postId") String postId);

    /**
     * 查询用户收藏的帖子 ID 列表（按收藏时间倒序，供 PageHelper 分页）
     * @param userId 用户ID
     * @return 帖子 ID 列表
     */
    List<String> listCollectedPostIdsByUser(@Param("userId") String userId);

    /**
     * 查询用户收藏过的全部帖子 ID（不限页，供 Redis 状态缓存冷启动用）
     * <p>与 listCollectedPostIdsByUser 的区别：那个走 PageHelper 分页用于列表展示，
     * 这个不分页，用于一次性把全量收藏状态灌进缓存。</p>
     * @param userId 用户ID
     * @return 帖子 ID 列表
     */
    List<String> listAllCollectedPostIdsByUser(@Param("userId") String userId);
}
