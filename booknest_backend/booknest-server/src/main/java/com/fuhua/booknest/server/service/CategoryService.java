package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.CategoryVO;

import java.util.List;

public interface CategoryService {

    /**
     * 查询分类树（两级：一级分类 + 其下子分类）
     * @return 分类树
     */
    List<CategoryVO> getCategoryTree();
}
