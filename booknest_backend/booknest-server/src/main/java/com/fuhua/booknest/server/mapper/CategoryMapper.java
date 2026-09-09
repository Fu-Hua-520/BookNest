package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    /**
     * 插入新分类
     * @param category 分类信息
     */
    void insert(Category category);

    /**
     * 根据分类ID查询分类
     * @param id 分类ID
     * @return 分类信息
     */
    Category selectById(@Param("id") String id);

    /**
     * 查询全部分类
     * @return 分类列表
     */
    List<Category> listAll();

    /**
     * 更新分类信息
     * @param category 分类信息
     */
    void update(Category category);

    /**
     * 根据分类ID删除分类
     * @param id 分类ID
     */
    void deleteById(@Param("id") String id);
}
