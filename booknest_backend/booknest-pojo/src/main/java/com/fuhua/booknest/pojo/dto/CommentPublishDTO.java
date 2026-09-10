package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 发布评论请求 DTO
 */
@Data
public class CommentPublishDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 评论内容
    @NotBlank(message = "评论内容不能为空")
    private String content;

    // 被回复的评论ID（回复某条评论时填写，顶级评论为空）
    private String replyId;
}
