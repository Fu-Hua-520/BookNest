package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.entity.Tag;
import com.fuhua.booknest.server.service.TagService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 标签管理
 * <p>标签名全局唯一；使用次数只读，由 post_tag 关联表实时统计（见 TagMapper.xml），后台不修改</p>
 */
@RestController
@RequestMapping("/admin/tag")
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "管理后台-标签管理")
public class TagManagerController {

    @Autowired
    private TagService tagService;

    /**
     * 分页查询标签（含已禁用标签）
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询标签")
    public Result<PageInfo<Tag>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(tagService.listTagPage(keyword, page, pageSize));
    }

    /**
     * 新增标签
     */
    @PostMapping
    @Operation(summary = "新增标签")
    public Result<String> create(@RequestBody Tag tag) {
        return Result.success(tagService.createTag(tag));
    }

    /**
     * 更新标签（重命名 / 启用禁用）
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新标签")
    public Result<String> update(@PathVariable String id, @RequestBody Tag tag) {
        tag.setId(id);
        tagService.updateTag(tag);
        return Result.success("更新成功");
    }

    /**
     * 删除标签（已被帖子引用时会被拒绝）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签")
    public Result<String> delete(@PathVariable String id) {
        tagService.deleteTag(id);
        return Result.success("删除成功");
    }
}
