package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.pojo.dto.ChatRequestDTO;
import com.fuhua.booknest.pojo.dto.ChatStreamEvent;
import com.fuhua.booknest.pojo.entity.AIMessage;
import com.fuhua.booknest.pojo.vo.RagHit;
import com.fuhua.booknest.server.service.AIConversationService;
import com.fuhua.booknest.server.service.AIService;
import com.fuhua.booknest.server.service.RagRetrievalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 流式对话服务实现
 *
 * <p>基于 Spring AI {@link ChatClient} 流式接口，将模型输出适配为前端 SSE 事件流，
 * 支持函数调用（工具调用）与 RAG 检索增强。所用 {@link ChatClient} 为
 * {@link com.fuhua.booknest.server.config.AIConfig} 装配的单例实例（已一次性注册
 * {@code BookTools}），不可在请求内重复 {@code builder.defaultTools(...)}。
 * 工具结果（TOOL_RESULT）在 Spring AI
 * 内部工具执行模式下由框架自行消化，不单独推送（见 {@link #toEvents}）。</p>
 */
@Service
@Slf4j
public class AIServiceImpl implements AIService {

    /** 默认模型 */
    private static final String DEFAULT_MODEL = "deepseek-chat";

    /** 系统提示词（普通对话） */
    private static final String SYSTEM_PROMPT =
            "你是 BookNest 书籍论坛的智能助手。你可以帮助用户搜索论坛帖子、查找书籍详情、"
                    + "查看公开书单、阅读帖子全文，并解答与读书相关的问题。"
                    + "回答时请保持友好、简洁，优先基于论坛内容与工具返回的结果作答。";

    /** 上下文记忆条数上限 */
    private static final int CONTEXT_LIMIT = 20;

    /** RAG 检索条数上限 */
    private static final int RAG_TOP_K = 5;

    /** RAG 单段内容截断长度 */
    private static final int RAG_CONTENT_MAX_LEN = 200;

    /** 工具调用展示名映射（工具名 -> 中文展示名） */
    private static final Map<String, String> TOOL_DISPLAY_NAMES = Map.of(
            "searchPosts", "搜帖子",
            "getBookInfo", "查书籍",
            "listBooklists", "查书单",
            "getPostContent", "读帖子"
    );

    @Autowired
    private ChatClient chatClient;
    @Autowired
    private AIConversationService conversationService;
    @Autowired(required = false)
    private RagRetrievalService ragRetrievalService;

    /**
     * 普通流式对话（无 RAG）
     * @param dto 对话请求
     * @return 流式事件流
     */
    @Override
    public Flux<ChatStreamEvent> streamChat(ChatRequestDTO dto) {
        return doStream(dto, null);
    }

    /**
     * RAG 增强流式对话
     * @param dto 对话请求
     * @return 流式事件流
     */
    @Override
    public Flux<ChatStreamEvent> streamChatWithRAG(ChatRequestDTO dto) {
        // RAG 服务条件装配（spring.ai.vectorstore.type=qdrant 时才存在），不存在则降级为普通对话
        if (ragRetrievalService == null) {
            log.info("RAG 检索服务未装配，降级为普通对话");
            return doStream(dto, null);
        }
        String ragSystemPrompt = null;
        try {
            List<RagHit> hits = ragRetrievalService.retrieve(dto.getMessage(), RAG_TOP_K);
            if (hits != null && !hits.isEmpty()) {
                String ragContext = buildRagContext(hits);
                ragSystemPrompt = SYSTEM_PROMPT + "以下是论坛相关内容，供参考回答：\n" + ragContext;
            }
        } catch (Exception e) {
            log.warn("RAG 检索失败，降级为普通对话，原因: {}", e.getMessage());
        }
        return doStream(dto, ragSystemPrompt);
    }

    /**
     * 核心流式对话流程
     *
     * <p>注意：用户ID与会话ID等 ThreadLocal 依赖数据在方法体内（订阅前）同步解析，
     * 后续响应式流水线运行在 Reactor 线程，不再访问 {@link BaseContext}。</p>
     *
     * @param dto 对话请求
     * @param ragSystemPrompt RAG 增强系统提示词（可为 null 表示普通对话）
     * @return 流式事件流
     */
    private Flux<ChatStreamEvent> doStream(ChatRequestDTO dto, String ragSystemPrompt) {
        String userId = BaseContext.getCurrentId();
        if (userId == null || userId.isBlank()) {
            return Flux.just(ChatStreamEvent.error(resolveSessionId(dto), "用户未登录"));
        }

        // 1. 确定 sessionId：无 conversationId 则新建会话
        final String sessionId;
        if (dto.getConversationId() == null || dto.getConversationId().isBlank()) {
            sessionId = conversationService.createConversation(userId);
        } else {
            // 校验会话归属，防止越权读取/写入他人会话
            conversationService.checkOwnership(dto.getConversationId(), userId);
            sessionId = dto.getConversationId();
        }
        final String model = resolveModel(dto);

        // 2. 组装消息（系统提示词 + 历史记忆 + 当前用户消息）
        List<Message> messages = buildMessages(sessionId, dto.getMessage(), ragSystemPrompt);

        // 3. 持久化用户消息
        conversationService.addMessage(buildUserMessage(sessionId, dto.getMessage(), model));

        // 4. 发起流式请求（单例 ChatClient 已在 AIConfig 中一次性注册 BookTools；
        //    冷流：订阅时才真正调用模型）
        Flux<ChatResponse> responseFlux = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse();

        // 累积助手回复文本与最后一次 token 用量（AtomicReference 保证闭包内可写）
        AtomicReference<StringBuilder> textBuilder = new AtomicReference<>(new StringBuilder());
        AtomicReference<Usage> lastUsage = new AtomicReference<>();

        // 5. 适配映射：每个 ChatResponse -> 若干 ChatStreamEvent
        Flux<ChatStreamEvent> mapped = responseFlux
                .flatMapIterable(response -> toEvents(response, sessionId, model, textBuilder, lastUsage));

        // 6. 先发 THINKING 事件，流结束后发 DONE 事件，并落库助手消息 + 更新会话元信息
        return Flux.concat(
                        Flux.just(ChatStreamEvent.thinking(sessionId)),
                        mapped
                )
                .concatWith(Flux.defer(() -> Flux.just(buildDoneEvent(sessionId, lastUsage.get()))))
                .doOnComplete(() -> persistAssistantAndUpdate(sessionId, model, textBuilder.get().toString(), lastUsage.get(), dto.getMessage()))
                .onErrorResume(e -> {
                    log.error("AI 流式对话异常, sessionId={}", sessionId, e);
                    return Flux.just(ChatStreamEvent.error(sessionId, e.getMessage()));
                });
    }

    /**
     * 将单个 ChatResponse 适配为一个或多个 ChatStreamEvent
     *
     * <p>工具调用：对每个 ToolCall 推送 TOOL_CALLING 事件。TOOL_RESULT 事件在 Spring AI
     * 内部工具执行模式下不单独推送（TODO：如需向用户展示工具结果，需改用 ToolCallback
     * 显式控制执行流程后再补齐）。文本片段：追加到累积文本并推送 MESSAGE 事件。</p>
     *
     * @param response    模型响应
     * @param sessionId   会话ID
     * @param model       模型名
     * @param textBuilder 累积文本
     * @param lastUsage   最后一次 token 用量
     * @return 事件列表
     */
    private List<ChatStreamEvent> toEvents(ChatResponse response, String sessionId, String model,
                                           AtomicReference<StringBuilder> textBuilder, AtomicReference<Usage> lastUsage) {
        List<ChatStreamEvent> events = new ArrayList<>();
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return events;
        }

        // 记录最后一次非空的 token 用量（流式响应中通常只有末尾一次携带 usage）
        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        if (usage != null) {
            lastUsage.set(usage);
        }

        AssistantMessage output = response.getResult().getOutput();

        // 工具调用：模型请求调用工具（此刻 text 为空）
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
        if (toolCalls != null && !toolCalls.isEmpty()) {
            for (AssistantMessage.ToolCall tc : toolCalls) {
                ChatStreamEvent toolCallingEvent = ChatStreamEvent.toolCalling(ChatStreamEvent.ToolCall.builder()
                        .toolName(tc.name())
                        .displayName(TOOL_DISPLAY_NAMES.getOrDefault(tc.name(), tc.name()))
                        .parameters(tc.arguments())
                        .status(ChatStreamEvent.ToolStatus.calling)
                        .build());
                // 补填会话ID，避免 SSE 事件 id 为空
                toolCallingEvent.setSessionId(sessionId);
                events.add(toolCallingEvent);
            }
            return events;
        }

        // 普通文本片段
        String text = output.getText();
        if (text != null && !text.isEmpty()) {
            textBuilder.get().append(text);
            events.add(ChatStreamEvent.message(sessionId, text, model));
        }
        return events;
    }

    /**
     * 组装对话消息列表：系统提示词 + 历史记忆 + 当前用户消息
     * @param conversationId  会话ID
     * @param userMessage     当前用户消息
     * @param ragSystemPrompt RAG 增强系统提示词（可为 null）
     * @return 消息列表
     */
    private List<Message> buildMessages(String conversationId, String userMessage, String ragSystemPrompt) {
        List<Message> messages = new ArrayList<>();
        // 系统提示词：优先 RAG 增强版，否则普通版
        String systemPrompt = ragSystemPrompt != null && !ragSystemPrompt.isBlank() ? ragSystemPrompt : SYSTEM_PROMPT;
        messages.add(new SystemMessage(systemPrompt));
        // 历史记忆（已反转为正序），跳过 system / error 等非对话角色
        List<AIMessage> context = conversationService.getContextMessages(conversationId, CONTEXT_LIMIT);
        if (context != null) {
            for (AIMessage msg : context) {
                if ("user".equals(msg.getRole())) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if ("assistant".equals(msg.getRole())) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }
        // 当前用户消息
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /**
     * 构建用户消息实体（role=user）
     * @param conversationId 会话ID
     * @param content        消息内容
     * @param model          模型名
     * @return 消息实体
     */
    private AIMessage buildUserMessage(String conversationId, String content, String model) {
        return AIMessage.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role("user")
                .content(content)
                .model(model)
                .isError(0)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 构建助手消息实体（role=assistant）
     * @param conversationId 会话ID
     * @param content        累积的助手回复全文
     * @param model          模型名
     * @param usage          token 用量（可为 null）
     * @return 消息实体
     */
    private AIMessage buildAssistantMessage(String conversationId, String content, String model, Usage usage) {
        return AIMessage.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role("assistant")
                .content(content)
                .tokenCount(usage != null ? usage.getTotalTokens() : null)
                .model(model)
                .isError(0)
                .createTime(LocalDateTime.now())
                .build();
    }

    /**
     * 流结束后：落库助手消息 + 更新会话 messageCount / title
     * @param sessionId  会话ID
     * @param model      模型名
     * @param content    助手回复全文
     * @param usage      token 用量
     * @param userMessage 当前用户消息（用于生成新会话标题）
     */
    private void persistAssistantAndUpdate(String sessionId, String model, String content, Usage usage, String userMessage) {
        if (content == null || content.isEmpty()) {
            log.warn("助手回复为空，跳过落库, sessionId={}", sessionId);
            return;
        }
        conversationService.addMessage(buildAssistantMessage(sessionId, content, model, usage));
        // 标题取首条用户消息前 20 字（仅当会话标题为空时生效）
        conversationService.updateConversationAfterReply(sessionId, buildTitle(userMessage));
    }

    /**
     * 构建 DONE 事件（携带 token 用量）
     * @param sessionId 会话ID
     * @param usage     token 用量（可为 null）
     * @return DONE 事件
     */
    private ChatStreamEvent buildDoneEvent(String sessionId, Usage usage) {
        ChatStreamEvent.TokenUsage tokenUsage = null;
        if (usage != null) {
            tokenUsage = ChatStreamEvent.TokenUsage.builder()
                    .inputTokens(usage.getPromptTokens())
                    .outputTokens(usage.getCompletionTokens())
                    .totalTokens(usage.getTotalTokens())
                    .build();
        }
        return ChatStreamEvent.done(sessionId, tokenUsage);
    }

    /**
     * 将 RAG 命中列表拼成上下文文本（标题/作者/内容片段，截断到合适长度）
     * @param hits 命中列表
     * @return 上下文文本
     */
    private String buildRagContext(List<RagHit> hits) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (RagHit hit : hits) {
            sb.append("[").append(index++).append("] ").append(hit.getTitle() == null ? "无标题" : hit.getTitle());
            if (hit.getAuthorName() != null) {
                sb.append("（作者：").append(hit.getAuthorName()).append("）");
            }
            sb.append("\n");
            if (hit.getContent() != null) {
                sb.append(truncate(hit.getContent(), RAG_CONTENT_MAX_LEN)).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成会话标题：取首条用户消息前 20 字
     * @param message 用户消息
     * @return 标题
     */
    private String buildTitle(String message) {
        if (message == null || message.isBlank()) {
            return "新对话";
        }
        return message.length() > 20 ? message.substring(0, 20) : message;
    }

    /**
     * 文本截断（超出长度追加省略号）
     * @param text   原文
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    /**
     * 解析模型名（空则用默认模型）
     * @param dto 对话请求
     * @return 模型名
     */
    private String resolveModel(ChatRequestDTO dto) {
        return dto.getModel() != null && !dto.getModel().isBlank() ? dto.getModel() : DEFAULT_MODEL;
    }

    /**
     * 解析会话ID（额度耗尽等异常场景下无真实会话时的占位）
     * @param dto 对话请求
     * @return 会话ID占位符
     */
    private String resolveSessionId(ChatRequestDTO dto) {
        return dto.getConversationId() != null && !dto.getConversationId().isBlank()
                ? dto.getConversationId() : "unknown";
    }
}
