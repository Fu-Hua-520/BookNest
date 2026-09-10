package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostLikeMapper {

    /**
     * 插入帖子点赞记录
     * @param postLike 点赞记录信息
     */
    void insert(PostLike postLike);

    /**
     * 根据帖子ID与用户ID删除点赞记录
     * @param postId 帖子ID
     * @param userId 用户ID
     */
    void deleteByPostAndUser(@Param("postId") String postId, @Param("userId") String userId);

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
}
