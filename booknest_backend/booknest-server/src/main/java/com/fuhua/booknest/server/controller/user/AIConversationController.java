package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.entity.AIConversation;
import com.fuhua.booknest.pojo.entity.AIMessage;
import com.fuhua.booknest.server.service.AIConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 会话管理控制器
 */
@RestController
@RequestMapping("/ai/conversations")
@Slf4j
@Tag(name = "AI会话")
public class AIConversationController {

    @Autowired
    private AIConversationService conversationService;

    /**
     * 查询当前用户的会话列表
     * @return 会话列表
     */
    @GetMapping
    @Operation(summary = "查询会话列表")
    public Result<List<AIConversation>> listConversations() {
        return Result.success(conversationService.getUserConversations(BaseContext.getCurrentId()));
    }

    /**
     * 查询会话的消息列表
     * @param id 会话ID
     * @return 消息列表
     */
    @GetMapping("/{id}/messages")
    @Operation(summary = "查询会话消息")
    public Result<List<AIMessage>> listMessages(@PathVariable String id) {
        return Result.success(conversationService.getConversationMessages(id, BaseContext.getCurrentId()));
    }

    /**
     * 更新会话标题
     * @param id    会话ID
     * @param title 新标题
     * @return 更新结果
     */
    @PostMapping("/{id}/title")
    @Operation(summary = "更新会话标题")
    public Result<String> updateTitle(@PathVariable String id, @RequestParam String title) {
        conversationService.updateConversationTitle(id, title);
        return Result.success("更新成功");
    }

    /**
     * 删除会话
     * @param id 会话ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除会话")
    public Result<String> deleteConversation(@PathVariable String id) {
        conversationService.deleteConversation(id, BaseContext.getCurrentId());
        return Result.success("删除成功");
    }
}
