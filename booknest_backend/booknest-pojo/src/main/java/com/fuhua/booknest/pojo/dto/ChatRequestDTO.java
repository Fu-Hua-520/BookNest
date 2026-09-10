package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 助手对话请求 DTO
 */
@Data
public class ChatRequestDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 用户输入的消息
    @NotBlank(message = "消息不能为空")
    private String message;

    // 会话ID（可空，为空则新建会话）
    private String conversationId;

    // 模型（可空，默认 deepseek-chat）
    private String model;
}
