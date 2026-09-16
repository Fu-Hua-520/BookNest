package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私信消息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMsg implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /**
     * 渠道 ID：私聊存会话 ID，群聊存群 ID。
     * 两者共用一个字段，历史消息查询（按 conversation_id 取）就能对私聊/群聊走同一条 SQL。
     */
    private String conversationId;
    private String senderId;
    /** 接收者：私聊填对方用户ID；群聊为 NULL（多接收者，落到 group_id） */
    private String receiverId;
    /** 群聊专有：群 ID；私聊为 NULL */
    private String groupId;
    private String content;
    /** 消息类型：TEXT 文本 / IMAGE 图片（图片时 content 存 URL） */
    private String msgType;
    private Integer isRead;
    private LocalDateTime createTime;
}
