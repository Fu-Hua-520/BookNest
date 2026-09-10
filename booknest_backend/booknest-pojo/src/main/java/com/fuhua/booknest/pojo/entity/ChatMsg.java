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
    private String conversationId;
    private String senderId;
    private String receiverId;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
}
