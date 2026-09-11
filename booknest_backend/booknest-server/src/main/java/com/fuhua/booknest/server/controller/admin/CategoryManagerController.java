package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.entity.Category;
import com.fuhua.booknest.pojo.vo.AdminCategoryVO;
import com.fuhua.booknest.server.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理后台 - 分类管理
 * <p>分类树固定两级：parentId 为空表示一级分类，否则为其父分类（一级）的 ID</p>
 */
@RestController
@RequestMapping("/admin/category")
@Slf4j
@Tag(name = "管理后台-分类管理")
public class CategoryManagerController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 查询完整分类树（含已禁用分类，供后台编辑）
     */
    @GetMapping("/tree")
    @Operation(summary = "查询完整分类树")
    public Result<List<AdminCategoryVO>> tree() {
        return Result.success(categoryService.getCategoryTreeForAdmin());
    }

    /**
     * 新增分类
     */
    @PostMapping
    @Operation(summary = "新增分类")
    public Result<String> create(@RequestBody Category category) {
        return Result.success(categoryService.createCategory(category));
    }

    /**
     * 更新分类
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新分类")
    public Result<String> update(@PathVariable String id, @RequestBody Category category) {
        category.setId(id);
        categoryService.updateCategory(category);
        return Result.success("更新成功");
    }

    /**
     * 删除分类（存在子分类或已被帖子引用时会被拒绝）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除分类")
    public Result<String> delete(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return Result.success("删除成功");
    }
}
