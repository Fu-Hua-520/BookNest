package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 评论区 AI 机器人配置。
 *
 * <p>每个机器人由用户自己创建并填入自己的 API Key，经管理员审核通过后，
 * 才可以在评论里被 {@code @机器人名} 触发回复。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBot implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 机器人ID（UUID） */
    private String id;

    /** 创建者用户ID */
    private String ownerId;

    /** 机器人名称，同时也是 @ 触发词，全站唯一 */
    private String name;

    /** 头像URL */
    private String avatar;

    /** 简介 */
    private String description;

    /** 模型厂商：deepseek / dashscope */
    private String provider;

    /** OpenAI 兼容 base-url（不含结尾 /v1） */
    private String baseUrl;

    /** 模型名 */
    private String model;

    /** 机器人自己的 API Key */
    private String apiKey;

    /** 系统提示词：设定 AI 的身份、语气、回答范围 */
    private String systemPrompt;

    /** 采样温度 */
    private BigDecimal temperature;

    /** 单条回复最大 token 数 */
    private Integer maxTokens;

    /** 审核状态 0待审 1通过 2驳回 */
    private Integer auditStatus;

    /** 驳回原因 */
    private String rejectReason;

    /** 创建者侧开关 1启用 0停用 */
    private Integer enabled;

    /** 累计回复条数 */
    private Integer replyCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
