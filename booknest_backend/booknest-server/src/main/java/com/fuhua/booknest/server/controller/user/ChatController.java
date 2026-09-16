package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.ChatConversationVO;
import com.fuhua.booknest.pojo.vo.ChatMsgVO;
import com.fuhua.booknest.server.service.ChatService;
import com.fuhua.booknest.server.websocket.WsSessionRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
@Slf4j
@Tag(name = "用户私信相关接口")
public class ChatController {

    @Autowired
    private ChatService chatService;
    @Autowired
    private WsSessionRegistry wsSessionRegistry;

    /**
     * 查询会话列表
     * @return 会话列表
     */
    @GetMapping("/conversations")
    @Operation(summary = "查询会话列表")
    public Result<List<ChatConversationVO>> getConversations() {
        return Result.success(chatService.getConversations(BaseContext.getCurrentId()));
    }

    /**
     * 获取或创建与目标用户的会话
     * @param targetUserId 目标用户ID
     * @return 会话ID
     */
    @PostMapping("/conversations/with/{targetUserId}")
    @Operation(summary = "获取或创建与目标用户的会话")
    public Result<String> getOrCreateConversationId(@PathVariable String targetUserId) {
        return Result.success(chatService.getOrCreateConversationId(BaseContext.getCurrentId(), targetUserId));
    }

    /**
     * 分页查询会话消息
     * @param convId 会话ID
     * @param page 页码（从1开始）
     * @param pageSize 每页条数
     * @return 消息列表
     */
    @GetMapping("/conversations/{convId}/messages")
    @Operation(summary = "查询会话消息记录")
    public Result<List<ChatMsgVO>> getMessages(@PathVariable String convId,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "50") int pageSize) {
        return Result.success(chatService.getMessages(convId, BaseContext.getCurrentId(), page, pageSize));
    }

    /**
     * 标记会话已读
     * @param convId 会话ID
     * @return 操作结果
     */
    @PostMapping("/conversations/{convId}/read")
    @Operation(summary = "标记会话已读")
    public Result<String> markAsRead(@PathVariable String convId) {
        chatService.markAsRead(convId, BaseContext.getCurrentId());
        return Result.success("已标记为已读");
    }

    /**
     * 查询未读消息总数
     * @return 未读消息数
     */
    @GetMapping("/unread-count")
    @Operation(summary = "查询未读消息总数")
    public Result<Integer> getUnreadCount() {
        return Result.success(chatService.getUnreadCount(BaseContext.getCurrentId()));
    }

    /**
     * 查询用户是否在线
     *
     * <p>在线态已改为跨实例判断（Redis ZSET），不再只看本进程的会话表 ——
     * 否则多实例部署时，连在别的实例上的用户会被报成离线。</p>
     *
     * @param userId 用户ID
     * @return 是否在线
     */
    @GetMapping("/user/{userId}/online")
    @Operation(summary = "查询用户是否在线")
    public Result<Boolean> isOnline(@PathVariable String userId) {
        return Result.success(wsSessionRegistry.isUserOnline(userId));
    }
}
