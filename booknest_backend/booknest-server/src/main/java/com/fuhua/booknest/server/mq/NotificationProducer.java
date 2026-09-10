package com.fuhua.booknest.server.mq;

import com.fuhua.booknest.server.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知消息生产者
 */
@Component
@Slf4j
public class NotificationProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送通知消息到 RabbitMQ
     * @param receiverId 接收者用户ID
     * @param type 通知类型
     * @param content 通知内容
     * @param sourceId 关联资源ID（可为空）
     */
    public void sendNotification(String receiverId, String type, String content, String sourceId) {
        NotificationMessage message = new NotificationMessage(receiverId, type, content, sourceId);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, message);
        log.info("通知消息已发送，receiverId: {}, type: {}", receiverId, type);
    }
}
