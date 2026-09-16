package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 创建 / 编辑 AI 机器人的请求体。
 *
 * <p>编辑时 {@code apiKey} 留空表示「沿用原来的 Key」，不用把密钥回传给前端再传回来。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiBotSaveDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 机器人名称（@ 触发词） */
    @NotBlank(message = "机器人名称不能为空")
    @Size(max = 20, message = "机器人名称最长 20 个字")
    private String name;

    /** 简介 */
    @Size(max = 200, message = "简介最长 200 个字")
    private String description;

    /** 头像URL */
    private String avatar;

    /** 模型厂商：deepseek / dashscope */
    @NotBlank(message = "请选择模型厂商")
    private String provider;

    /** 模型名；留空则用该厂商默认模型 */
    private String model;

    /** API Key；编辑时留空表示不修改 */
    private String apiKey;

    /** 系统提示词：设定 AI 的身份与风格 */
    @Size(max = 2000, message = "系统提示词最长 2000 个字")
    private String systemPrompt;

    /** 采样温度 0~2 */
    private Double temperature;

    /** 单条回复最大 token 数 */
    private Integer maxTokens;

    /** 创建者侧开关（编辑时可传，创建时忽略） */
    private Boolean enabled;
}
