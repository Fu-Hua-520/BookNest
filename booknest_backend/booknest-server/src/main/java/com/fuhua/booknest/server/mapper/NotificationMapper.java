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
