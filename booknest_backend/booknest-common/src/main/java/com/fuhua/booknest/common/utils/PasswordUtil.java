package com.fuhua.booknest.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码加密工具类
 * 基于 BCrypt（带盐哈希）进行密码加密与校验，替代无盐 MD5
 */
public class PasswordUtil {

    /**
     * BCrypt 编码器单例
     */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /**
     * 对原始密码进行 BCrypt 加密
     *
     * @param raw 原始密码
     * @return 加密后的密码串
     */
    public static String encrypt(String raw) {
        return ENCODER.encode(raw);
    }

    /**
     * 校验原始密码与加密后的密码是否匹配
     *
     * @param raw     原始密码
     * @param encoded 加密后的密码
     * @return 是否匹配
     */
    public static boolean matches(String raw, String encoded) {
        return ENCODER.matches(raw, encoded);
    }
}
