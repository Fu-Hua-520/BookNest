package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    /**
     * 插入通知
     * @param notification 通知信息
     */
    void insert(Notification notification);

    /**
     * 幂等插入通知：撞 {@code uk_notification_dedup} 唯一键时返回 0 而不抛异常。
     *
     * <p>MQ 消费语义是 at-least-once：消费成功但 ack 丢失、或消费中途抛异常触发重试，
     * 同一条消息都会被再投一次。用 {@code insert ignore} 把「重复投递」变成一次
     * 影响行数为 0 的正常返回，消费者才能把重复消息当成「已经投过了」而不是错误。</p>
     *
     * @param notification 通知信息
     * @return 实际插入行数（1 = 首次插入，0 = 重复消息已忽略）
     */
    int insertIgnore(Notification notification);

    /**
     * 根据通知ID查询通知
     * @param id 通知ID
     * @return 通知信息
     */
    Notification selectById(@Param("id") Long id);

    /**
     * 根据接收者查询通知列表（按创建时间倒序）
     * @param receiverId 接收者用户ID
     * @return 通知列表
     */
    List<Notification> listByReceiverId(@Param("receiverId") String receiverId);

    /**
     * 统计未读通知数
     * @param receiverId 接收者用户ID
     * @return 未读通知数
     */
    long countUnread(@Param("receiverId") String receiverId);

    /**
     * 标记单条通知已读
     * @param id 通知ID
     */
    void markRead(@Param("id") Long id);

    /**
     * 标记全部通知已读
     * @param receiverId 接收者用户ID
     */
    void markAllRead(@Param("receiverId") String receiverId);

    /**
     * 根据通知ID删除通知
     * @param id 通知ID
     */
    void deleteById(@Param("id") Long id);
}
