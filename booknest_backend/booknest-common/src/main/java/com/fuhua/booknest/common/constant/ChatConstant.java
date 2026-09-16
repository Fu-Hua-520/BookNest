package com.fuhua.booknest.common.constant;

/**
 * 私信/群聊相关常量
 */
public class ChatConstant {

    /** 渠道类型：私聊 */
    public static final String TYPE_PRIVATE = "PRIVATE";
    /** 渠道类型：群聊 */
    public static final String TYPE_GROUP = "GROUP";

    /** 消息类型：文本 */
    public static final String MSG_TEXT = "TEXT";
    /** 消息类型：图片（content 存图片 URL） */
    public static final String MSG_IMAGE = "IMAGE";

    /** 群成员角色：群主 */
    public static final String ROLE_OWNER = "OWNER";
    /** 群成员角色：普通成员 */
    public static final String ROLE_MEMBER = "MEMBER";

    /* ---------------- 群邀请 / 加入申请 ---------------- */

    /**
     * 邀请类型：INVITE = 群内成员邀请站内书友（被邀请人来确认）
     */
    public static final String INVITE_TYPE_INVITE = "INVITE";

    /**
     * 邀请类型：JOIN_REQUEST = 用户主动申请加入某个群（群主来审批）
     */
    public static final String INVITE_TYPE_JOIN = "JOIN_REQUEST";

    /** 处理状态：待处理 */
    public static final String INVITE_PENDING = "PENDING";
    /** 处理状态：已同意 */
    public static final String INVITE_ACCEPTED = "ACCEPTED";
    /** 处理状态：已拒绝 */
    public static final String INVITE_REJECTED = "REJECTED";

    /** 群人数上限 */
    public static final int GROUP_MEMBER_LIMIT = 200;

    /**
     * 归一化消息类型：只允许 TEXT / IMAGE，其余一律按 TEXT 处理。
     * 消息类型会决定前端渲染成文字气泡还是图片气泡，不能让客户端随意透传。
     * @param msgType 前端传入的类型
     * @return TEXT 或 IMAGE
     */
    public static String normalizeMsgType(String msgType) {
        if (msgType == null) {
            return MSG_TEXT;
        }
        return MSG_IMAGE.equalsIgnoreCase(msgType.trim()) ? MSG_IMAGE : MSG_TEXT;
    }
}
