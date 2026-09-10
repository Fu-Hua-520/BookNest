package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.PostComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PostCommentMapper {

    /**
     * 插入评论
     * @param postComment 评论信息
     */
    void insert(PostComment postComment);

    /**
     * 根据评论ID查询评论
     * @param id 评论ID
     * @return 评论信息
     */
    PostComment selectById(@Param("id") String id);

    /**
     * 根据评论ID删除评论
     * @param id 评论ID
     */
    void deleteById(@Param("id") String id);

    /**
     * 根据帖子ID查询评论列表（顶级评论与回复统一按时间正序，前端组装楼层）
     * @param postId 帖子ID
     * @return 评论列表
     */
    List<PostComment> listByPostId(@Param("postId") String postId);

    /**
     * 评论点赞数 +1
     * @param id 评论ID
     */
    void incrementLikeCount(@Param("id") String id);

    /**
     * 评论点赞数 -1
     * @param id 评论ID
     */
    void decrementLikeCount(@Param("id") String id);
}
