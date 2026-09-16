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
 *
 * <p><b>为什么必须幂等：</b>MQ 只能保证 at-least-once，同一条消息被投两次是常态而非异常 ——
 * 消费成功但 ack 丢失、消费途中应用重启、失败重试（本项目 yml 配了 3 次重试），
 * 都会让同一条消息再走一遍。原实现是裸 {@code insert}，重投一次就在通知列表里
 * 多一条一模一样的记录；重试 3 次而全部失败时更是产生 3 条重复，然后才进死信队列。</p>
 *
 * <p>幂等靠数据库唯一键 {@code uk_notification_dedup} + {@code insert ignore} 实现，
 * 而不是在应用层判重：唯一键的判定和插入是同一条语句、同一个原子操作，
 * 并发重复投递也不会漏网。</p>
 *
 * <p>判定重复用「业务四元组」{@code (receiver_id, type, source_id, anchor_id)}，
 * 故意<b>不含 content 和时间</b>：同一个人因为同一条评论/帖子收到的同类型通知，
 * 业务上本来就只该有一条；内容一样只是佐证，不是判重条件。</p>
 *
 * <p>⚠️ <b>该唯一键建在一个 STORED 生成列 {@code dedup_key} 上</b>，而不是直接建在
 * 那四个业务列上。原因是 MySQL 唯一索引逐列判等、而 <b>NULL 之间互不相等</b> ——
 * 四列里只要有任意一列是 NULL，这一行就永远不可能与任何行冲突，键等于没建。
 * 本项目 LIKE / COMMENT / FOLLOW 三类通知的 {@code anchor_id} 恒为 NULL
 * （FOLLOW 连 {@code source_id} 也是），正好全都落在这个盲区里。
 * 生成列把 NULL 折叠成空串后再拼接，去重才真正覆盖全部通知类型。
 * 生成列由 MySQL 自动维护，本类与实体都不需要感知它。</p>
 */
@Component
@Slf4j
public class NotificationConsumer {

    @Autowired
    private NotificationMapper notificationMapper;

    /**
     * 消费通知消息，落库 notification 表。
     *
     * <p>重复消息走 {@code insert ignore} 影响行数为 0 的分支，正常返回即可 ——
     * 它已经「落库」（上一次投递落的），没有任何理由让它进重试或死信队列。
     * 只有真正的异常（DB 连不上、列超长）才抛出去，交给 Spring AMQP 的重试 + DLX 兜底。</p>
     *
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
                    .anchorId(message.getAnchorId())
                    .isRead(0)
                    .createTime(LocalDateTime.now())
                    .build();
            int rows = notificationMapper.insertIgnore(notification);
            if (rows == 0) {
                // 重投：上次已经落过库了。这不是异常，debug 级别足够
                log.debug("通知重复投递，已幂等忽略: receiverId={}, type={}, sourceId={}, anchorId={}",
                        message.getReceiverId(), message.getType(), message.getSourceId(), message.getAnchorId());
            }
        } catch (Exception e) {
            log.error("通知消息落库失败，将进入重试/死信队列，receiverId: {}, type: {}",
                    message.getReceiverId(), message.getType(), e);
            throw new RuntimeException("通知落库失败", e);
        }
    }
}
