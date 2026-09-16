package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostComment implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String postId;
    /** 评论者用户ID；AI 机器人回复时为 null */
    private String userId;
    /** AI 机器人ID；真人评论时为 null。（userId 与 botId 二选一有值） */
    private String botId;
    private String content;
    private String replyId;
    private Integer likeCount;
    private LocalDateTime createTime;
}
