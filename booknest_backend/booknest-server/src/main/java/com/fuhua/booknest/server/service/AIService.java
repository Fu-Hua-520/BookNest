package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.ChatRequestDTO;
import com.fuhua.booknest.pojo.dto.ChatStreamEvent;
import reactor.core.publisher.Flux;

/**
 * AI 流式对话服务接口
 */
public interface AIService {

    /**
     * 普通流式对话（无 RAG）
     * @param dto 对话请求
     * @return 流式事件流（THINKING / TOOL_CALLING / MESSAGE / DONE / ERROR）
     */
    Flux<ChatStreamEvent> streamChat(ChatRequestDTO dto);

    /**
     * RAG 增强流式对话
     * @param dto 对话请求
     * @return 流式事件流
     */
    Flux<ChatStreamEvent> streamChatWithRAG(ChatRequestDTO dto);
}
