package com.fuhua.booknest.server.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuhua.booknest.common.constant.RedisConstant;
import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.ChatRequestDTO;
import com.fuhua.booknest.pojo.dto.ChatStreamEvent;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.AIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * AI 助手控制器（SSE 流式对话 + 额度管控）
 */
@RestController
@RequestMapping("/ai")
@Slf4j
@Tag(name = "AI助手")
public class AIController {

    /** 额度耗尽提示文案 */
    private static final String QUOTA_EXHAUSTED_MSG = "抱歉，本日免费提问额度已用完，请明日再来或开通会员享受无限提问。";

    @Autowired
    private AIService aiService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 普通流式对话（SSE）
     * @param dto 对话请求
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "普通流式对话")
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ChatRequestDTO dto) {
        String userId = BaseContext.getCurrentId();
        // 额度校验：不足时返回额度耗尽流，不发起真实 LLM 请求
        if (!checkAndDeductQuota(userId)) {
            String sessionId = dto.getConversationId() != null ? dto.getConversationId() : "unknown";
            return Flux.just(
                            ChatStreamEvent.message(sessionId, QUOTA_EXHAUSTED_MSG, null),
                            ChatStreamEvent.error(sessionId, "额度不足"))
                    .map(this::createSSE);
        }
        return aiService.streamChat(dto).map(this::createSSE);
    }

    /**
     * RAG 增强流式对话（SSE）
     * @param dto 对话请求
     * @return SSE 事件流
     */
    @PostMapping(value = "/chat/rag", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "RAG 增强流式对话")
    public Flux<ServerSentEvent<String>> streamChatWithRAG(@Valid @RequestBody ChatRequestDTO dto) {
        String userId = BaseContext.getCurrentId();
        if (!checkAndDeductQuota(userId)) {
            String sessionId = dto.getConversationId() != null ? dto.getConversationId() : "unknown";
            return Flux.just(
                            ChatStreamEvent.message(sessionId, QUOTA_EXHAUSTED_MSG, null),
                            ChatStreamEvent.error(sessionId, "额度不足"))
                    .map(this::createSSE);
        }
        return aiService.streamChatWithRAG(dto).map(this::createSSE);
    }

    /**
     * 查询当前用户剩余提问额度
     * @return 剩余额度（会员返回 -1 表示不限）
     */
    @GetMapping("/quota")
    @Operation(summary = "查询提问额度")
    public Result<Integer> getQuota() {
        String userId = BaseContext.getCurrentId();
        if (userId == null || userId.isBlank()) {
            return Result.error("用户未登录");
        }
        User user = userMapper.getUserById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        // 会员（userLevel != 0）不限额度
        if (user.getUserLevel() != null && user.getUserLevel() != 0) {
            return Result.success(-1);
        }
        String value = stringRedisTemplate.opsForValue().get(RedisConstant.AI_QUOTA + userId);
        int remaining = RedisConstant.AI_QUOTA_FREE;
        if (value != null) {
            try {
                remaining = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("额度缓存值异常，回退默认额度: {}", value);
                remaining = RedisConstant.AI_QUOTA_FREE;
            }
        }
        return Result.success(remaining);
    }

    /**
     * 额度检查与扣减：会员不限；普通用户用 Redis 计数器扣减
     * @param userId 用户ID
     * @return true 表示有额度可用
     */
    private boolean checkAndDeductQuota(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        User user = userMapper.getUserById(userId);
        if (user == null || user.getUserLevel() == null) {
            return false;
        }
        // 会员（userLevel != 0）不限额度
        if (user.getUserLevel() != 0) {
            return true;
        }
        // 首次初始化免费额度（仅当 key 不存在时设置）
        String key = RedisConstant.AI_QUOTA + userId;
        stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(RedisConstant.AI_QUOTA_FREE));
        // 扣减一次额度，返回值 >= 0 表示仍有额度
        Long remaining = stringRedisTemplate.opsForValue().decrement(key);
        return remaining != null && remaining >= 0;
    }

    /**
     * 将 ChatStreamEvent 转换为 SSE 事件（data 为 JSON 字符串）
     * @param event 流式事件
     * @return SSE 事件
     */
    private ServerSentEvent<String> createSSE(ChatStreamEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("SSE 事件序列化失败", e);
            json = "{\"type\":\"ERROR\",\"error\":\"序列化失败\"}";
        }
        String eventName = event.getType() != null ? event.getType().name().toLowerCase() : "message";
        return ServerSentEvent.<String>builder()
                .id(event.getSessionId())
                .event(eventName)
                .data(json)
                .build();
    }
}
