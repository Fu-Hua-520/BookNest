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
    /** 评论者用户ID；AI 机器人回复时为 null */
    private String userId;
    private String userName;
    private String userAvatar;
    /**
     * AI 机器人ID；真人评论为 null。
     * 有值时 userName / userAvatar 已经换成该机器人的昵称与头像，
     * 前端靠它渲染「AI」徽标，并且不再把昵称链到个人主页（机器人没有用户主页）。
     */
    private String botId;
    /** 机器人创建者用户ID：用于判断当前用户能否删除这条 AI 回复 */
    private String botOwnerId;
    private String content;
    private String replyId;
    private String replyToUserName;
    private Integer likeCount;
    private LocalDateTime createTime;
    private Boolean liked;
}
