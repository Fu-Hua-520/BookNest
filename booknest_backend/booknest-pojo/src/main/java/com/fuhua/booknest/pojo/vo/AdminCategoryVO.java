package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台书吧节点 VO
 * 相比用户端 CategoryVO，额外携带状态、描述与时间戳，供后台编辑表格使用。
 * children 保留自「两级分类树」时期，书吧已取消分级后恒为空。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    /** 书吧图标 URL；为空表示未上传，前端用吧名首字兜底 */
    private String icon;
    /** 【遗留】书吧已不分级，恒为 null */
    private String parentId;
    private Integer sortOrder;
    private String description;
    /** 状态：0-禁用 1-启用 */
    private Integer status;
    /** 吧主用户ID；为 null 表示官方吧（后台直接建的吧 / 被收回吧主的吧） */
    private String ownerId;
    /** 吧主昵称（查不到用户时为 null） */
    private String ownerName;
    /** 审核状态：0-待审核 1-已通过 2-已驳回（后台直接建的吧恒为 1） */
    private Integer auditStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 【遗留】书吧已不分级，恒为空 */
    private List<AdminCategoryVO> children;
}
