package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 关注/粉丝列表的用户摘要 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String avatar;
}
