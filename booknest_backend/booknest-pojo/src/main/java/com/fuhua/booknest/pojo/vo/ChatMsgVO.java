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
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatar;
    private String receiverId;
    private String content;
    private Integer isRead;
    private LocalDateTime createTime;
    private Boolean isMine;
}
