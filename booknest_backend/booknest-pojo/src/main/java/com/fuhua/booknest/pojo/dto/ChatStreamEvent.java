package com.fuhua.booknest.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * AI 助手 SSE 流式响应事件契约
 * <p>
 * 前端通过 SSE 逐条接收本对象序列化后的 JSON，事件类型由 {@link EventType} 区分，
 * 各字段名与前端契约严格一致，不得修改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatStreamEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    // 事件类型
    private EventType type;

    // MESSAGE 事件的文本片段
    private String content;

    // 会话ID
    private String sessionId;

    // 使用的模型
    private String model;

    // 错误信息（ERROR 事件）
    private String error;

    // token 用量统计
    private TokenUsage tokenUsage;

    // 工具调用信息（TOOL_CALLING / TOOL_RESULT 事件）
    private ToolCall toolCall;

    // 推荐结果列表（DONE 事件携带）
    private List<RecommendItem> recommendations;

    /**
     * 构造 THINKING 事件（开始思考）
     * @param sessionId 会话ID
     * @return 事件
     */
    public static ChatStreamEvent thinking(String sessionId) {
        return ChatStreamEvent.builder().type(EventType.THINKING).sessionId(sessionId).build();
    }

    /**
     * 构造 TOOL_CALLING 事件（开始调用工具）
     * @param toolCall 工具调用信息
     * @return 事件
     */
    public static ChatStreamEvent toolCalling(ToolCall toolCall) {
        return ChatStreamEvent.builder().type(EventType.TOOL_CALLING).toolCall(toolCall).build();
    }

    /**
     * 构造 TOOL_RESULT 事件（工具调用完成）
     * @param toolCall 工具调用信息
     * @return 事件
     */
    public static ChatStreamEvent toolResult(ToolCall toolCall) {
        return ChatStreamEvent.builder().type(EventType.TOOL_RESULT).toolCall(toolCall).build();
    }

    /**
     * 构造 MESSAGE 事件（普通文本片段）
     * @param sessionId 会话ID
     * @param content 文本内容
     * @param model 模型
     * @return 事件
     */
    public static ChatStreamEvent message(String sessionId, String content, String model) {
        return ChatStreamEvent.builder().type(EventType.MESSAGE).sessionId(sessionId).content(content).model(model).build();
    }

    /**
     * 构造 DONE 事件（结束，携带 token 用量）
     * @param sessionId 会话ID
     * @param tokenUsage token 用量
     * @return 事件
     */
    public static ChatStreamEvent done(String sessionId, TokenUsage tokenUsage) {
        return ChatStreamEvent.builder().type(EventType.DONE).sessionId(sessionId).tokenUsage(tokenUsage).build();
    }

    /**
     * 构造 DONE 事件（结束，携带 token 用量与推荐结果）
     * @param sessionId 会话ID
     * @param tokenUsage token 用量
     * @param recommendations 推荐结果
     * @return 事件
     */
    public static ChatStreamEvent doneWithRecommendations(String sessionId, TokenUsage tokenUsage, List<RecommendItem> recommendations) {
        return ChatStreamEvent.builder().type(EventType.DONE).sessionId(sessionId).tokenUsage(tokenUsage).recommendations(recommendations).build();
    }

    /**
     * 构造 ERROR 事件
     * @param sessionId 会话ID
     * @param message 错误信息
     * @return 事件
     */
    public static ChatStreamEvent error(String sessionId, String message) {
        return ChatStreamEvent.builder().type(EventType.ERROR).sessionId(sessionId).error(message).build();
    }

    /**
     * SSE 事件类型枚举
     */
    public enum EventType {
        THINKING,
        TOOL_CALLING,
        TOOL_RESULT,
        MESSAGE,
        DONE,
        ERROR
    }

    /**
     * token 用量统计
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TokenUsage implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;
    }

    /**
     * 工具调用信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolCall implements Serializable {
        private static final long serialVersionUID = 1L;

        // 工具名（如 searchPosts）
        private String toolName;
        // 展示名（如「搜索帖子」）
        private String displayName;
        // 图标
        private String icon;
        // 调用参数（JSON 字符串）
        private String parameters;
        // 工具调用状态
        private ToolStatus status;
        // 结果数量
        private Integer resultCount;
        // 工具结果（JSON 字符串）
        private String toolResult;
    }

    /**
     * 工具调用状态枚举
     */
    public enum ToolStatus {
        calling,
        success,
        failed,
        no_result
    }

    /**
     * 推荐结果条目
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String type;
        private String title;
        private String description;
        private String coverImage;
        private String author;
        private Long viewCount;
        private Double rating;
        private List<String> tags;
        private String link;
        private String sourceUrl;
        private String siteName;
    }
}
