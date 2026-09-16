package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI 机器人简要信息 VO（公开）。
 *
 * <p>评论区 @ 选择器、机器人广场都用它。这里<b>只有</b>能公开展示的字段：
 * 不包含 API Key、base-url、系统提示词等任何配置细节。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBotBriefVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String avatar;
    private String description;
    /** 厂商展示名 */
    private String providerLabel;
    private String model;
    /** 累计回复条数 */
    private Integer replyCount;
}
