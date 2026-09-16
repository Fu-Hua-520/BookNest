package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.Notification;
import com.fuhua.booknest.pojo.vo.NotificationVO;
import com.fuhua.booknest.server.mapper.NotificationMapper;
import com.fuhua.booknest.server.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Override
    public List<NotificationVO> listNotifications() {
        String currentId = BaseContext.getCurrentId();
        List<Notification> notifications = notificationMapper.listByReceiverId(currentId);
        List<NotificationVO> result = new ArrayList<>();
        if (notifications == null) {
            return result;
        }
        for (Notification notification : notifications) {
            result.add(toNotificationVO(notification));
        }
        return result;
    }

    @Override
    public long unreadCount() {
        return notificationMapper.countUnread(BaseContext.getCurrentId());
    }

    @Override
    public void markRead(Long id) {
        // 校验通知存在且属于当前用户
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getReceiverId().equals(BaseContext.getCurrentId())) {
            throw new BaseException("无权限操作");
        }
        notificationMapper.markRead(id);
    }

    @Override
    public void markAllRead() {
        notificationMapper.markAllRead(BaseContext.getCurrentId());
    }

    @Override
    public void deleteNotification(Long id) {
        // 校验通知存在且属于当前用户
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getReceiverId().equals(BaseContext.getCurrentId())) {
            throw new BaseException("无权限操作");
        }
        notificationMapper.deleteById(id);
    }

    /**
     * 组装通知 VO
     * @param notification 通知实体
     * @return 通知 VO
     */
    private NotificationVO toNotificationVO(Notification notification) {
        return NotificationVO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .content(notification.getContent())
                .sourceId(notification.getSourceId())
                .anchorId(notification.getAnchorId())
                .isRead(notification.getIsRead())
                .createTime(notification.getCreateTime())
                .build();
    }
}
