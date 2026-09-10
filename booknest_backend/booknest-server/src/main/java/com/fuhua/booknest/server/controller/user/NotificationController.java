package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.NotificationVO;
import com.fuhua.booknest.server.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notification")
@Slf4j
@Tag(name = "通知相关接口")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * 查询当前用户的通知列表
     * @return 通知列表
     */
    @GetMapping("/list")
    @Operation(summary = "查询通知列表")
    public Result<List<NotificationVO>> listNotifications() {
        return Result.success(notificationService.listNotifications());
    }

    /**
     * 查询当前用户未读通知数
     * @return 未读通知数
     */
    @GetMapping("/unread-count")
    @Operation(summary = "查询未读通知数")
    public Result<Long> unreadCount() {
        return Result.success(notificationService.unreadCount());
    }

    /**
     * 标记单条通知已读
     * @param id 通知ID
     * @return 标记结果
     */
    @PutMapping("/{id}/read")
    @Operation(summary = "标记单条通知已读")
    public Result<String> markRead(@PathVariable Long id) {
        notificationService.markRead(id);
        return Result.success("已读");
    }

    /**
     * 标记全部通知已读
     * @return 标记结果
     */
    @PutMapping("/read-all")
    @Operation(summary = "标记全部通知已读")
    public Result<String> markAllRead() {
        notificationService.markAllRead();
        return Result.success("全部已读");
    }

    /**
     * 删除单条通知
     * @param id 通知ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除通知")
    public Result<String> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return Result.success("删除成功");
    }
}
