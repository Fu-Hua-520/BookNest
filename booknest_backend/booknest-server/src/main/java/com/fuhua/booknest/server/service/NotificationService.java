package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.NotificationVO;

import java.util.List;

public interface NotificationService {

    /**
     * 查询当前用户的通知列表
     * @return 通知列表
     */
    List<NotificationVO> listNotifications();

    /**
     * 查询当前用户未读通知数
     * @return 未读通知数
     */
    long unreadCount();

    /**
     * 标记单条通知已读
     * @param id 通知ID
     */
    void markRead(Long id);

    /**
     * 标记全部通知已读
     */
    void markAllRead();

    /**
     * 删除单条通知
     * @param id 通知ID
     */
    void deleteNotification(Long id);
}
