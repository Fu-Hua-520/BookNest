package com.fuhua.booknest.server.controller.admin;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.vo.UserLoginVO;
import com.fuhua.booknest.server.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理后台 - 认证接口
 * 独立于用户端登录：令牌使用 adminSecretKey 签发，仅能通过管理端拦截器校验
 *
 * 注意：/admin/login 已在 WebMvcConfiguration 中排除于管理端拦截器之外，
 * 否则会出现"登录接口本身要求先登录"的死循环
 */
@RestController
@RequestMapping("/admin")
@Slf4j
@Tag(name = "管理后台-认证")
public class AdminAuthController {

    @Autowired
    private AdminAuthService adminAuthService;

    /**
     * 管理员登录
     */
    @PostMapping("/login")
    @Operation(summary = "管理员登录")
    public Result<UserLoginVO> login(@RequestBody @Valid UserLoginDTO userLoginDTO) {
        // 仅打印登录标识，不打印密码明文
        log.info("管理员登录：email={}", userLoginDTO.getEmail());
        return Result.success(adminAuthService.adminLogin(userLoginDTO));
    }
}
