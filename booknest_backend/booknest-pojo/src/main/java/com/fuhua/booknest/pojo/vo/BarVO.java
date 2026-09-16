package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 书吧 VO（贴吧式「吧」的展示单元）。
 *
 * 复用 category 表承载：一个一级分类即一个吧。与 CategoryVO（发帖选择用的
 * 两级分类树）的区别是，这里把展示一个「吧」所需的附加信息都拍平了 ——
 * 吧主、帖子数、审核状态，前端拿到即可直接渲染卡片，不必再额外请求。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    /** 书吧图标 URL；为空时前端用吧名首字兜底 */
    private String icon;
    private String description;
    /** 吧主用户ID（官方吧为空） */
    private String ownerId;
    /** 吧主昵称（官方吧为空，前端展示为「官方」） */
    private String ownerName;
    private String ownerAvatar;
    /** 吧内帖子数（只统计已发布且已过审的帖子） */
    private Long postCount;
    /** 吧内成员数（= 关注数；热门书吧榜按这个排序，其余列表可不填） */
    private Long memberCount;
    /** 创建审核状态：0-待审核 1-已通过 2-已驳回（仅「我的申请」列表会用到） */
    private Integer auditStatus;
    /** 驳回原因（仅 auditStatus=2 时有值） */
    private String rejectReason;
    private LocalDateTime createTime;
}
