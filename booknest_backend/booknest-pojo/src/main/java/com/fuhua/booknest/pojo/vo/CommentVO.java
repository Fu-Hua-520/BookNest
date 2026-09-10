package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论 VO（含用户信息与点赞状态）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String postId;
    private String userId;
    private String userName;
    private String userAvatar;
    private String content;
    private String replyId;
    private String replyToUserName;
    private Integer likeCount;
    private LocalDateTime createTime;
    private Boolean liked;
}
