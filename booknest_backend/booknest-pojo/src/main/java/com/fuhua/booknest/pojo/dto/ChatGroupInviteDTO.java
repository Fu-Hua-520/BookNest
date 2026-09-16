package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 邀请成员入群 DTO
 */
@Data
public class ChatGroupInviteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 待邀请的用户 ID 列表 */
    @NotEmpty(message = "请至少选择一位书友")
    private List<String> userIds;
}
