package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 书吧等级称号（由吧主自定义）
 *
 * <p>只有吧主设过的等级才有记录。没有记录的等级前端只显示「Lv.N」——
 * 这就是「默认无称号、但等级字段一定存在」的含义。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarLevelTitle implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /** 书吧ID（category.id） */
    private String barId;
    /** 等级（1~10） */
    private Integer level;
    /** 称号名 */
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
