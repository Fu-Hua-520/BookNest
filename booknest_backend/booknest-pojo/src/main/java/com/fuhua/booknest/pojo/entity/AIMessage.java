package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 消息实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String conversationId;
    private String role;
    private String content;
    private Integer tokenCount;
    private String toolCalls;
    private String recommendations;
    private String model;
    private Integer isError;
    private LocalDateTime createTime;
}
