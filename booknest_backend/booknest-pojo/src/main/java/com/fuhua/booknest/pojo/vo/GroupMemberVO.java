package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群成员 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMemberVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String avatar;
    /** OWNER / MEMBER */
    private String role;
    private LocalDateTime joinTime;
}
