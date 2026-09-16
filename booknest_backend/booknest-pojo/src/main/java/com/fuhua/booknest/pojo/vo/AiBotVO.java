package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 机器人详情/列表 VO（创建者与管理端共用）。
 *
 * <p>API Key 只以脱敏形式 {@link #apiKeyMasked} 返回，明文永不外发。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBotVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String ownerId;
    /** 创建者昵称 */
    private String ownerName;
    private String name;
    private String avatar;
    private String description;
    private String provider;
    /** 厂商展示名 */
    private String providerLabel;
    private String baseUrl;
    private String model;
    /** 脱敏后的 API Key，如 sk-12****ab */
    private String apiKeyMasked;
    /** 是否已配置过 API Key（编辑时前端提示「留空即不修改」） */
    private Boolean apiKeySet;
    private String systemPrompt;
    private Double temperature;
    private Integer maxTokens;
    private Integer auditStatus;
    private String rejectReason;
    private Integer enabled;
    private Integer replyCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 该厂商的可选模型，供前端下拉 */
    private List<String> modelOptions;
}
