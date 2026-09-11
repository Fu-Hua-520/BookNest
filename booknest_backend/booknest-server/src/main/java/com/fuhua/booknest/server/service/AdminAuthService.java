package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.vo.UserLoginVO;

/**
 * 管理后台认证服务
 * 与用户端登录相互独立：使用管理端专属密钥与有效期签发令牌
 */
public interface AdminAuthService {

    /**
     * 管理员登录
     * 校验账号密码与管理员角色，通过后使用 adminSecretKey 签发管理端令牌
     *
     * @param userLoginDTO 登录信息（邮箱 + 密码）
     * @return 登录信息（含管理端令牌）
     */
    UserLoginVO adminLogin(UserLoginDTO userLoginDTO);
}
