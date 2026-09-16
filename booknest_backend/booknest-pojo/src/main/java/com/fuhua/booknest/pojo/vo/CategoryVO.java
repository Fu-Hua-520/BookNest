package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 书吧 VO（发帖时选择目标吧 / 侧栏书吧列表用）
 *
 * 历史包袱：字段名与 children 保留自「两级分类树」时期，但书吧已取消分级，
 * children 恒为空、parentId 恒为 null，前端按平铺列表渲染即可。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    /** 书吧图标 URL；为空时前端用吧名首字兜底 */
    private String icon;
    /** 【遗留】书吧已不分级，恒为 null */
    private String parentId;
    private Integer sortOrder;
    /** 【遗留】书吧已不分级，恒为空 */
    private List<CategoryVO> children;
}
