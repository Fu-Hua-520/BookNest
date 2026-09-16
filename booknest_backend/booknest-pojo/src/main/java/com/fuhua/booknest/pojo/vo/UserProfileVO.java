package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户资料 VO（个人主页 / 更新资料后回显用）
 *
 * 与 UserLoginVO 的区别：不带 token，也不含密码。
 * 之所以单独建一个而不是复用 UserLoginVO，是为了让「更新资料」这条路径
 * 天然不可能把令牌带进响应体里。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String account;
    private String username;
    /** 头像 URL；为空表示未设置，前端用昵称首字兜底 */
    private String avatar;
    private String phone;
    private String email;
    private Integer userLevel;
    private String role;
    private LocalDateTime createTime;
}
