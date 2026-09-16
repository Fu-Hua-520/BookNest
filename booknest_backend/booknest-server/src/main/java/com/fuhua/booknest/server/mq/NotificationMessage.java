package com.fuhua.booknest.server.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知消息体（RabbitMQ 消息载体）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationMessage {

    private String receiverId;
    private String type;
    private String content;
    private String sourceId;
    // 锚点ID（可为空）：REPLY 传评论 ID，前端据此滚到那条评论
    private String anchorId;
}
