package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户简要信息（用于搜索选择，如吧主任命管理员时挑人）
 *
 * <p>刻意只带 id / username / avatar —— 不带手机号与邮箱。
 * 这个 VO 会被返回给任意登录用户，泄露联系方式是不可接受的。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String username;
    private String avatar;
}
