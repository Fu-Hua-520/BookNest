package com.fuhua.booknest.server.mq;

import com.fuhua.booknest.pojo.entity.Notification;
import com.fuhua.booknest.server.config.RabbitMQConfig;
import com.fuhua.booknest.server.mapper.NotificationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 通知消息消费者（异步落库）
 */
@Component
@Slf4j
public class NotificationConsumer {

    @Autowired
    private NotificationMapper notificationMapper;

    /**
     * 消费通知消息，落库 notification 表
     * @param message 通知消息
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void onNotification(NotificationMessage message) {
        try {
            Notification notification = Notification.builder()
                    .receiverId(message.getReceiverId())
                    .type(message.getType())
                    .content(message.getContent())
                    .sourceId(message.getSourceId())
                    .isRead(0)
                    .createTime(LocalDateTime.now())
                    .build();
            notificationMapper.insert(notification);
        } catch (Exception e) {
            // 异常仅记录日志，不抛出，避免消息循环重投
            log.error("通知消息落库失败，receiverId: {}, type: {}", message.getReceiverId(), message.getType(), e);
        }
    }
}
