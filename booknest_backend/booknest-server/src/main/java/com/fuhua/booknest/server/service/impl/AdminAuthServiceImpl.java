package com.fuhua.booknest.server.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fuhua.booknest.common.constant.JwtClaimsConstant;
import com.fuhua.booknest.common.constant.MessageConstant;
import com.fuhua.booknest.common.constant.StatusConstant;
import com.fuhua.booknest.common.exception.AccountLockedException;
import com.fuhua.booknest.common.exception.AccountNotFoundException;
import com.fuhua.booknest.common.exception.PasswordErrorException;
import com.fuhua.booknest.common.properties.JwtProperties;
import com.fuhua.booknest.common.utils.JwtUtil;
import com.fuhua.booknest.common.utils.PasswordUtil;
import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.UserLoginVO;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.AdminAuthService;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理后台认证服务实现
 */
@Service
@Slf4j
public class AdminAuthServiceImpl implements AdminAuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public UserLoginVO adminLogin(UserLoginDTO userLoginDTO) {
        User user = userMapper.getUserByEmail(userLoginDTO.getEmail());
        // 账号不存在：统一提示"账号或密码错误"，避免通过错误信息枚举已注册邮箱
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        // 账号被禁用时拒绝登录
        if (StatusConstant.DISABLE.equals(user.getStatus())) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        if (!PasswordUtil.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        // 非管理员不得通过管理端登录
        // 同样返回模糊提示：对外不暴露"该邮箱存在但不是管理员"
        if (!MessageConstant.ROLE_ADMIN.equals(user.getRole())) {
            log.warn("管理端登录被拒，账号非管理员（用户ID: {}, 角色: {}）", user.getId(), user.getRole());
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        // 使用管理端专属密钥与有效期签发令牌，与用户端令牌完全隔离
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.ROLE, user.getRole());

        String token = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);
        log.info("管理员登录成功，已签发管理端 token（用户ID: {}）", user.getId());

        return UserLoginVO.builder()
                .id(user.getId())
                .account(user.getAccount())
                .username(user.getUsername())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .userLevel(user.getUserLevel())
                .status(user.getStatus())
                .role(user.getRole())
                .token(token)
                .build();
    }
}
