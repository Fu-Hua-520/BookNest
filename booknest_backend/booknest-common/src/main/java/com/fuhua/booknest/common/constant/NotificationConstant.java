package com.fuhua.booknest.common.constant;

/**
 * 通知类型常量
 */
public class NotificationConstant {

    // 点赞通知
    public static final String TYPE_LIKE = "LIKE";

    // 评论通知
    public static final String TYPE_COMMENT = "COMMENT";

    // 回复评论通知（别人回复了你写的评论）
    public static final String TYPE_REPLY = "REPLY";

    // 关注通知
    public static final String TYPE_FOLLOW = "FOLLOW";

    // 审核通知
    public static final String TYPE_AUDIT = "AUDIT";

    // AI 机器人回复通知（评论区 @ 了机器人，机器人回复了你）
    public static final String TYPE_AI_REPLY = "AI_REPLY";
}
