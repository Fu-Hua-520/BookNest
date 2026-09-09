package com.fuhua.booknest.server.controller.user;

import com.fuhua.booknest.common.result.Result;
import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.dto.UserRegisterDTO;
import com.fuhua.booknest.pojo.vo.UserLoginVO;
import com.fuhua.booknest.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@Slf4j
@Tag(name = "用户相关接口")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * @param userRegisterDTO 注册信息
     * @return 注册结果
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<String> userRegister(@RequestBody UserRegisterDTO userRegisterDTO) {
        log.info("用户注册：phone={}, email={}", userRegisterDTO.getPhone(), userRegisterDTO.getEmail());

        userService.userRegister(userRegisterDTO);

        log.info("用户注册成功");
        return Result.success("注册成功");
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<UserLoginVO> userLogin(@RequestBody UserLoginDTO userLoginDTO){
        log.info("用户登录: {}",userLoginDTO);
        UserLoginVO userLoginVO=userService.userLogin(userLoginDTO);
        log.info("用户登录成功");
        return Result.success(userLoginVO);
    }
}
