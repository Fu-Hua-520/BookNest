package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostCommentLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PostCommentLikeMapper {

    /**
     * 插入评论点赞记录
     * @param postCommentLike 点赞记录信息
     */
    void insert(PostCommentLike postCommentLike);

    /**
     * 根据评论ID与用户ID删除点赞记录
     * @param commentId 评论ID
     * @param userId 用户ID
     */
    void deleteByCommentAndUser(@Param("commentId") String commentId, @Param("userId") String userId);

    /**
     * 根据帖子ID删除该帖下所有评论的点赞记录（级联删除帖子时调用）
     * @param postId 帖子ID
     */
    void deleteByPostId(@Param("postId") String postId);

    /**
     * 根据评论ID删除该评论的所有点赞记录（级联删除评论时调用）
     * @param commentId 评论ID
     */
    void deleteByCommentId(@Param("commentId") String commentId);

    /**
     * 根据评论ID与用户ID查询点赞记录
     * @param commentId 评论ID
     * @param userId 用户ID
     * @return 点赞记录（不存在返回 null）
     */
    PostCommentLike selectByCommentAndUser(@Param("commentId") String commentId, @Param("userId") String userId);
}
