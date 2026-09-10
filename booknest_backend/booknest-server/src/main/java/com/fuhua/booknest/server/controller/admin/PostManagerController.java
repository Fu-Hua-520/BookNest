package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.entity.Post;
import com.fuhua.booknest.server.service.PostAdminService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - 帖子管理
 */
@RestController
@RequestMapping("/admin/post")
@Slf4j
@Tag(name = "管理后台-帖子")
public class PostManagerController {

    @Autowired
    private PostAdminService postAdminService;

    /**
     * 分页查询帖子列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询帖子列表")
    public Result<PageInfo<Post>> list(
            @RequestParam(required = false) Integer auditStatus,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(postAdminService.listPosts(auditStatus, status, page, pageSize));
    }

    /**
     * 审核帖子
     */
    @PostMapping("/{id}/audit")
    @Operation(summary = "审核帖子")
    public Result<String> audit(@PathVariable String id,
                                @RequestParam Integer auditStatus,
                                @RequestParam(required = false) String auditReason) {
        postAdminService.auditPost(id, auditStatus, auditReason);
        return Result.success("审核成功");
    }

    /**
     * 设置帖子置顶
     */
    @PostMapping("/{id}/top")
    @Operation(summary = "设置帖子置顶")
    public Result<String> setTop(@PathVariable String id, @RequestParam Integer isTop) {
        postAdminService.setPostTop(id, isTop);
        return Result.success("设置成功");
    }

    /**
     * 设置帖子状态
     */
    @PostMapping("/{id}/status")
    @Operation(summary = "设置帖子状态")
    public Result<String> setStatus(@PathVariable String id, @RequestParam Integer status) {
        postAdminService.setPostStatus(id, status);
        return Result.success("设置成功");
    }
}
