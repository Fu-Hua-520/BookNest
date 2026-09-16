package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私信消息 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMsgVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /** 渠道 ID：私聊=会话ID，群聊=群ID */
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String receiverId;
    /** 群聊专有：群 ID */
    private String groupId;
    /** 群聊专有：群名称 */
    private String groupName;
    /** 渠道类型：PRIVATE 私聊 / GROUP 群聊；前端据此决定用哪套已读与接口 */
    private String chatType;
    /** 消息类型：TEXT 文本 / IMAGE 图片 */
    private String msgType;
    /** 文本内容，或图片 URL（msgType=IMAGE 时） */
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
    private Boolean isMine;
}
