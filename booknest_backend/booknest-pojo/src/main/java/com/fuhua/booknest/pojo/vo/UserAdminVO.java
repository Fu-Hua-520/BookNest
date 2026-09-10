package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理后台用户列表 VO（不含密码）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAdminVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String account;
    private String username;
    private String phone;
    private String email;
    private String avatar;
    private Integer userLevel;
    private Integer status;
    private String role;
    private LocalDateTime createTime;
}
