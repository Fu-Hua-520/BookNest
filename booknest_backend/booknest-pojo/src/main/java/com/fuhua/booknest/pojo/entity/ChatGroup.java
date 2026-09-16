package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群聊实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /** 群名称 */
    private String name;
    /** 群头像（可选） */
    private String avatar;
    /** 群主用户ID */
    private String ownerId;
    /** 群公告 */
    private String notice;
    /** 最后一条消息预览 */
    private String lastMessage;
    /** 最后消息时间 */
    private LocalDateTime lastMsgAt;
    private LocalDateTime createTime;
}
