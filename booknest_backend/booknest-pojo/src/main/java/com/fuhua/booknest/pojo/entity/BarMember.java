package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 书吧成员：关注书吧 = 入吧
 *
 * <p>一张表同时承载三件事，避免「关注表 / 等级表」两份数据对不上：
 * 关注关系、吧内累计经验、吧内等级。</p>
 *
 * <p>level 是 exp 的<b>快照</b>：列表与排序直接读列，不用每次现算；
 * 换算阈值见服务端 BarLevelService，改阈值后需要重建一次快照。</p>
 *
 * <p>daily_exp / daily_date 服务于「每日经验上限」：
 * daily_date 不等于今天时，daily_exp 自动视为 0 并重置，不需要定时任务。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarMember implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /** 书吧ID（category.id） */
    private String barId;
    private String userId;
    /** 吧内累计经验 */
    private Integer exp;
    /** 吧内等级（exp 换算的快照） */
    private Integer level;
    /** 当日已获得经验（每日上限用） */
    private Integer dailyExp;
    /** daily_exp 归属日期，跨天自动重置 */
    private LocalDate dailyDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
