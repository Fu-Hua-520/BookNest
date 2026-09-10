package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIConversation implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String title;
    private String model;
    private Integer messageCount;
    private String lastMessagePreview;
    private Integer isPinned;
    private Integer isArchived;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
