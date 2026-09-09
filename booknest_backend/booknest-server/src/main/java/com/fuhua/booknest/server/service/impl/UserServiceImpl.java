package com.fuhua.booknest.server.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fuhua.booknest.common.constant.JwtClaimsConstant;
import com.fuhua.booknest.common.constant.MessageConstant;
import com.fuhua.booknest.common.constant.StatusConstant;
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
        // 1. 检查手机号是否已注册
        String userPhone = userRegisterDTO.getPhone();
        User userByPhone = getUserByPhone(userPhone);
        if (userByPhone != null) {
            throw new UserExistedException("手机号已被注册");
        }

        // 2. 检查邮箱是否已注册
        String userEmail = userRegisterDTO.getEmail();
        User userByEmail = getUserByEmail(userEmail);
        if (userByEmail != null) {
            throw new UserExistedException("邮箱已被注册");
        }

        // 3. 生成账户编号（带重试机制，防止重复）
        String account = generateUniqueAccount();

        // 4. 构建用户对象
        User insertUser = User.builder()
                .id(UUID.randomUUID().toString())
                .account(account)
                .username(MessageConstant.DEFAULT_USERNAME_PREFIX+account)  // 用户名默认为空，用户后期自己设置
                .password(PasswordUtil.encrypt(userRegisterDTO.getPassword()))
                .phone(userPhone)
                .email(userEmail)
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
            throw new PasswordErrorException("邮箱出错,未找到用户");
        }
        if (!PasswordUtil.matches(userLoginDTO.getPassword(), user.getPassword())) {
            throw new PasswordErrorException("密码错误，请重新输入");
        }

        // 根据用户角色选择不同的密钥和过期时间
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());

        String token;
        if ("ADMIN".equals(user.getRole())) {
            // 管理员使用 adminSecretKey
            token = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);
            log.info("管理员登录，使用 adminSecretKey 生成token");
        } else {
            // 普通用户使用 userSecretKey
            token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);
            log.info("普通用户登录，使用 userSecretKey 生成token");
        }

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
