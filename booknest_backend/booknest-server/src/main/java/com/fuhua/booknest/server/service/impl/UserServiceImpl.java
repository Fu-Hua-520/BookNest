package com.fuhua.booknest.server.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fuhua.booknest.common.constant.JwtClaimsConstant;
import com.fuhua.booknest.common.constant.MessageConstant;
import com.fuhua.booknest.common.constant.StatusConstant;
import com.fuhua.booknest.common.exception.AccountLockedException;
import com.fuhua.booknest.common.exception.AccountNotFoundException;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.common.exception.PasswordErrorException;
import com.fuhua.booknest.common.exception.UserExistedException;
import com.fuhua.booknest.common.properties.JwtProperties;
import com.fuhua.booknest.common.utils.AccountGenerator;
import com.fuhua.booknest.common.utils.JwtUtil;
import com.fuhua.booknest.common.utils.PasswordUtil;
import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.dto.UserRegisterDTO;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.UserLoginVO;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.UserService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public User getUserById(String userId) {
        return userMapper.getUserById(userId);
    }

    /**
     * 用户注册
     * @param userRegisterDTO
     */
    @Override
    public void userRegister(UserRegisterDTO userRegisterDTO) {
        // 0. 校验手机号与邮箱至少填一个，否则注册的账号无法登录
        String userPhone = userRegisterDTO.getPhone();
        String userEmail = userRegisterDTO.getEmail();
        boolean hasPhone = userPhone != null && !userPhone.trim().isEmpty();
        boolean hasEmail = userEmail != null && !userEmail.trim().isEmpty();
        if (!hasPhone && !hasEmail) {
            throw new BaseException("手机号或邮箱至少填一个");
        }
        // 空串视为未填，统一置 null 存储，避免 UNIQUE 列出现空串冲突
        String phone = hasPhone ? userPhone : null;
        String email = hasEmail ? userEmail : null;

        // 1. 检查手机号是否已注册
        if (hasPhone) {
            User userByPhone = getUserByPhone(userPhone);
            if (userByPhone != null) {
                throw new UserExistedException("手机号已被注册");
            }
        }

        // 2. 检查邮箱是否已注册
        if (hasEmail) {
            User userByEmail = getUserByEmail(userEmail);
            if (userByEmail != null) {
                throw new UserExistedException("邮箱已被注册");
            }
        }

        // 3. 生成账户编号（带重试机制，防止重复）
        String account = generateUniqueAccount();

        // 4. 构建用户对象
        User insertUser = User.builder()
                .id(UUID.randomUUID().toString())
                .account(account)
                .username(MessageConstant.DEFAULT_USERNAME_PREFIX+account)  // 用户名默认为空，用户后期自己设置
                .password(PasswordUtil.encrypt(userRegisterDTO.getPassword()))
                .phone(phone)
                .email(email)
                .avatar(MessageConstant.DEFAULT_AVATAR_URL)
                .userLevel(MessageConstant.USER_DEFAULT)
                .status(StatusConstant.ENABLE)
                .role(MessageConstant.ROLE_USER)
                .build();

        // 5. 插入数据库
        userMapper.insertUser(insertUser);
    }

    /**
     * 生成唯一的账户编号（带重试机制）
     */
    private String generateUniqueAccount() {
        int maxRetries = 10;
        for (int i = 0; i < maxRetries; i++) {
            String account = AccountGenerator.generate();
            // 检查账户是否已存在
            User existUser = userMapper.getUserByAccount(account);
            if (existUser == null) {
                return account;
            }
        }
        throw new RuntimeException("生成账户编号失败，请重试");
    }

    @Override
    public User getUserByPhone(String userPhone) {
        return userMapper.getUserByPhone(userPhone);
    }

    @Override
    public User getUserByEmail(String userEmail) {
        return userMapper.getUserByEmail(userEmail);
    }

    @Override
    public UserLoginVO userLogin(UserLoginDTO userLoginDTO) {
        String userEmail = userLoginDTO.getEmail();
        User user = userMapper.getUserByEmail(userEmail);
        if (user == null) {
            // 统一返回"账号或密码错误"，防止通过错误信息枚举已注册邮箱
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        // 账号被禁用时拒绝登录
        if (StatusConstant.DISABLE.equals(user.getStatus())) {
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        if (!PasswordUtil.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.ACCOUNT_OR_PASSWORD_ERROR);
        }

        // 统一使用 userSecretKey 签发 JWT（admin 独立密钥与 admin 拦截器留待 P5 管理后台实现）
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());

        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
        log.info("用户登录成功，签发token（用户ID: {}）", user.getId());

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
