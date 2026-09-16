package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 书吧管理员（由吧主任命）
 *
 * <p>吧主本人不在这个表里 —— 吧主身份看 category.owner_id。
 * 判断「某人能不能管某个吧」时两个来源都要查：owner_id 命中即为吧主，
 * 本表命中即为管理员。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarModerator implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /** 书吧ID（category.id） */
    private String barId;
    /** 被任命的用户ID */
    private String userId;
    private LocalDateTime createTime;
}
