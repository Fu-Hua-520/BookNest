package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 私信会话 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String peerId;
    private String peerName;
    private String peerAvatar;
    private String lastMessage;
    private LocalDateTime lastMsgAt;
    private Integer unreadCount;
}
