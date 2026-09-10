package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私信会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String user1Id;
    private String user2Id;
    private String lastMessage;
    private LocalDateTime lastMsgAt;
    private LocalDateTime createTime;
}
