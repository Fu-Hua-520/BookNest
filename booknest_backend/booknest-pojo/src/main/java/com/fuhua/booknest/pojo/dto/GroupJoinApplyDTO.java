package com.fuhua.booknest.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 申请加入群聊请求 DTO
 */
@Data
public class GroupJoinApplyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 申请理由（可空），会展示给群主作为审批参考 */
    private String message;
}
