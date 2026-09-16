package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建群聊 DTO
 */
@Data
public class ChatGroupCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 群名称 */
    @NotBlank(message = "群名称不能为空")
    @Size(max = 30, message = "群名称不超过 30 个字")
    private String name;

    /** 群头像（可选，直接存 OSS URL） */
    private String avatar;

    /** 群公告（可选） */
    @Size(max = 200, message = "群公告不超过 200 个字")
    private String notice;

    /**
     * 初始成员用户 ID 列表（不含群主，群主由登录态推导）
     * 单群人数上限在 Service 层校验，这里只拦明显异常的长度
     */
    @Size(max = 199, message = "单次最多邀请 199 人")
    private List<String> memberIds;
}
