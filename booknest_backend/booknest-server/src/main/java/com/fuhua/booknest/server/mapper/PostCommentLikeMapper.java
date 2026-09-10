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
     * 根据评论ID与用户ID查询点赞记录
     * @param commentId 评论ID
     * @param userId 用户ID
     * @return 点赞记录（不存在返回 null）
     */
    PostCommentLike selectByCommentAndUser(@Param("commentId") String commentId, @Param("userId") String userId);
}
