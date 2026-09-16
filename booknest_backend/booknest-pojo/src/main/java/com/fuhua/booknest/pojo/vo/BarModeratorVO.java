package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 书吧管理员（吧务列表用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarModeratorVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String avatar;
    private LocalDateTime createTime;
}
