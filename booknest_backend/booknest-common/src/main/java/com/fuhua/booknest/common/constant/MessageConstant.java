package com.fuhua.booknest.common.constant;

public class MessageConstant {
    
    //1.账号密码相关常量 
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_ALREADY_EXISTS = "账号已存在";    
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String PASSWORD_ERROR = "密码错误";

    public static final String ACCOUNT_OR_PASSWORD_ERROR = "账号或密码错误";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String DEFAULT_USERNAME_PREFIX="user_";
    // TODO: P2 接入 booknest 自有 OSS 后替换为默认头像 URL
    public static final String DEFAULT_AVATAR_URL="";

    public static final Integer USER_DEFAULT=0;
    public static final Integer USER_VIP=1;
    public static final Integer USER_SUPER_VIP=2;

    public static final String ROLE_USER="USER";
    public static final String ROLE_ADMIN="ADMIN";

    public static final String UPLOAD_FAILED="文件上传失败，请重试";
    public static final String UPLOAD_TOO_FREQUENT="上传过于频繁，请稍后再试";
    public static final String UPLOAD_UNAUTHORIZED="登录状态异常，请重新登录后再上传";
}
