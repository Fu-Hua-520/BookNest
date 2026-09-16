package com.fuhua.booknest.server.mq;

import com.fuhua.booknest.server.config.RabbitMQConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 通知消息生产者
 *
 * <p><b>为什么所有发送都走 {@link #sendNotification} 的「提交后投递」语义：</b>
 * 生产者在业务事务内被调用（评论、点赞、回复都在 {@code @Transactional} 里发通知）。
 * 如果直接在事务内 {@code convertAndSend}，会有两个实际隐患：
 * ① <b>发送失败会把业务一起拖回滚</b> —— MQ 抖动时抛异常，用户看到的是「评论失败」，
 *    可评论本身明明已经写好了，这属于典型的「辅助功能拖垮主流程」；
 * ② <b>事务还没提交就通知别人</b> —— 消费端可能先于提交读到数据，出现「通知点进去 404」。
 * 所以统一改成 afterCommit 投递：事务成功提交后才发，失败只记日志。</p>
 *
 * <p>没有活动事务时（例如 {@code FollowServiceImpl.follow} 未加 {@code @Transactional}）
 * 直接同步发送，行为与改造前一致。</p>
 */
@Component
@Slf4j
public class NotificationProducer {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送通知消息到 RabbitMQ（事务提交后投递）
     * @param receiverId 接收者用户ID
     * @param type 通知类型
     * @param content 通知内容
     * @param sourceId 关联资源ID（可为空）
     */
    public void sendNotification(String receiverId, String type, String content, String sourceId) {
        sendNotification(receiverId, type, content, sourceId, null);
    }

    /**
     * 发送通知消息到 RabbitMQ（带锚点，事务提交后投递）
     * @param receiverId 接收者用户ID
     * @param type 通知类型
     * @param content 通知内容
     * @param sourceId 关联资源ID（可为空）
     * @param anchorId 锚点ID（可为空）：REPLY 传评论 ID，前端点通知后滚到那条评论
     */
    public void sendNotification(String receiverId, String type, String content, String sourceId, String anchorId) {
        NotificationMessage message = new NotificationMessage(receiverId, type, content, sourceId, anchorId);
        // 若当前在事务中，延后到提交后发送；否则立即发送。
        // 注意：afterCommit 不换线程，仍在请求线程上，但此时事务已提交、DB 连接已归还，
        // 发送失败不会再影响业务结果的可见性。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(message);
                }
            });
        } else {
            doSend(message);
        }
    }

    /**
     * 真正投递消息。异常只记日志：
     * 走到这里业务事务已经提交，通知发不出去是「少一条通知」，
     * 不该以任何形式回吐给已经成功完成的业务操作。
     */
    private void doSend(NotificationMessage message) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, message);
            log.info("通知消息已发送，receiverId: {}, type: {}", message.getReceiverId(), message.getType());
        } catch (Exception e) {
            // 有 publisher-confirm 时，真正的「进了交换机但没进队列」由 confirm 回调兜底，
            // 这里只兜「连接不上 / 发送直接抛异常」这一类。
            log.error("通知消息发送失败（业务已提交，仅丢失本条通知）: receiverId={}, type={}",
                    message.getReceiverId(), message.getType(), e);
        }
    }
}
