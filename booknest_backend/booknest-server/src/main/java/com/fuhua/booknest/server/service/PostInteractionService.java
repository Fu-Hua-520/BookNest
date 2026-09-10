package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.CommentPublishDTO;
import com.fuhua.booknest.pojo.vo.CommentVO;

import java.util.List;
import java.util.Map;

public interface PostInteractionService {

    /**
     * 发布评论（含楼中楼回复）
     * @param postId 帖子ID
     * @param dto 评论信息
     * @return 评论 VO
     */
    CommentVO publishComment(String postId, CommentPublishDTO dto);

    /**
     * 查询帖子评论列表
     * @param postId 帖子ID
     * @return 评论列表
     */
    List<CommentVO> listComments(String postId);

    /**
     * 删除评论
     * @param commentId 评论ID
     */
    void deleteComment(String commentId);

    /**
     * 评论点赞/取消点赞
     * @param commentId 评论ID
     * @return {liked, likeCount}
     */
    Map<String, Object> toggleCommentLike(String commentId);

    /**
     * 帖子点赞/取消点赞
     * @param postId 帖子ID
     * @return {liked, likeCount}
     */
    Map<String, Object> togglePostLike(String postId);

    /**
     * 查询当前用户是否已点赞帖子
     * @param postId 帖子ID
     * @return 是否已点赞
     */
    boolean isPostLiked(String postId);

    /**
     * 帖子收藏/取消收藏
     * @param postId 帖子ID
     * @return {collected, collectCount}
     */
    Map<String, Object> togglePostCollect(String postId);

    /**
     * 查询当前用户是否已收藏帖子
     * @param postId 帖子ID
     * @return 是否已收藏
     */
    boolean isPostCollected(String postId);
}
