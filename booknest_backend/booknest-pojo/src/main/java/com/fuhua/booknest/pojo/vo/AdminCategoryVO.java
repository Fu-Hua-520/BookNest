package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台分类树节点 VO
 * 相比用户端 CategoryVO，额外携带状态、描述与时间戳，供后台编辑表格使用
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String parentId;
    private Integer sortOrder;
    private String description;
    /** 状态：0-禁用 1-启用 */
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 子分类（仅一级分类有值） */
    private List<AdminCategoryVO> children;
}
