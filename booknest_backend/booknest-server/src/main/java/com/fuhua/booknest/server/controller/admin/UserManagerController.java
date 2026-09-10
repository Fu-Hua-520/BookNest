package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.vo.UserAdminVO;
import com.fuhua.booknest.server.service.UserAdminService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 管理后台 - 用户管理
 */
@RestController
@RequestMapping("/admin/user")
@Slf4j
@Tag(name = "管理后台-用户")
public class UserManagerController {

    @Autowired
    private UserAdminService userAdminService;

    /**
     * 分页查询用户列表
     */
    @GetMapping("/list")
    @Operation(summary = "分页查询用户列表")
    public Result<PageInfo<UserAdminVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(userAdminService.listUsers(keyword, status, page, pageSize));
    }

    /**
     * 设置用户状态
     */
    @PostMapping("/{id}/status")
    @Operation(summary = "设置用户状态")
    public Result<String> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        userAdminService.updateUserStatus(id, status);
        return Result.success("设置成功");
    }

    /**
     * 设置用户角色
     */
    @PostMapping("/{id}/role")
    @Operation(summary = "设置用户角色")
    public Result<String> updateRole(@PathVariable String id, @RequestParam String role) {
        userAdminService.updateUserRole(id, role);
        return Result.success("设置成功");
    }
}
