package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 分类树节点 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String parentId;
    private Integer sortOrder;
    private List<CategoryVO> children;
}
