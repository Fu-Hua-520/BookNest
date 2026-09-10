package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.CommentPublishDTO;
import com.fuhua.booknest.pojo.vo.CommentVO;
import com.fuhua.booknest.server.service.PostInteractionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/post")
@Slf4j
@Tag(name = "帖子互动相关接口")
public class PostInteractionController {

    @Autowired
    private PostInteractionService postInteractionService;

    /**
     * 发布评论（含楼中楼回复）
     * @param postId 帖子ID
     * @param dto 评论信息
     * @return 评论 VO
     */
    @PostMapping("/{postId}/comment")
    @Operation(summary = "发布评论")
    public Result<CommentVO> publishComment(@PathVariable String postId, @RequestBody @Valid CommentPublishDTO dto) {
        return Result.success(postInteractionService.publishComment(postId, dto));
    }

    /**
     * 查询帖子评论列表
     * @param postId 帖子ID
     * @return 评论列表
     */
    @GetMapping("/{postId}/comment")
    @Operation(summary = "查询帖子评论列表")
    public Result<List<CommentVO>> listComments(@PathVariable String postId) {
        return Result.success(postInteractionService.listComments(postId));
    }

    /**
     * 删除评论
     * @param commentId 评论ID
     * @return 删除结果
     */
    @DeleteMapping("/comment/{commentId}")
    @Operation(summary = "删除评论")
    public Result<String> deleteComment(@PathVariable String commentId) {
        postInteractionService.deleteComment(commentId);
        return Result.success("删除成功");
    }

    /**
     * 评论点赞/取消点赞
     * @param commentId 评论ID
     * @return {liked, likeCount}
     */
    @PostMapping("/comment/{commentId}/like")
    @Operation(summary = "评论点赞/取消点赞")
    public Result<Map<String, Object>> toggleCommentLike(@PathVariable String commentId) {
        return Result.success(postInteractionService.toggleCommentLike(commentId));
    }

    /**
     * 帖子点赞/取消点赞
     * @param postId 帖子ID
     * @return {liked, likeCount}
     */
    @PostMapping("/{postId}/like")
    @Operation(summary = "帖子点赞/取消点赞")
    public Result<Map<String, Object>> togglePostLike(@PathVariable String postId) {
        return Result.success(postInteractionService.togglePostLike(postId));
    }

    /**
     * 查询当前用户是否已点赞帖子
     * @param postId 帖子ID
     * @return 是否已点赞
     */
    @GetMapping("/{postId}/like/status")
    @Operation(summary = "查询帖子点赞状态")
    public Result<Boolean> isPostLiked(@PathVariable String postId) {
        return Result.success(postInteractionService.isPostLiked(postId));
    }

    /**
     * 帖子收藏/取消收藏
     * @param postId 帖子ID
     * @return {collected, collectCount}
     */
    @PostMapping("/{postId}/collect")
    @Operation(summary = "帖子收藏/取消收藏")
    public Result<Map<String, Object>> togglePostCollect(@PathVariable String postId) {
        return Result.success(postInteractionService.togglePostCollect(postId));
    }

    /**
     * 查询当前用户是否已收藏帖子
     * @param postId 帖子ID
     * @return 是否已收藏
     */
    @GetMapping("/{postId}/collect/status")
    @Operation(summary = "查询帖子收藏状态")
    public Result<Boolean> isPostCollected(@PathVariable String postId) {
        return Result.success(postInteractionService.isPostCollected(postId));
    }
}
