package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.dto.UserRegisterDTO;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.UserBriefVO;
import com.fuhua.booknest.pojo.vo.UserLoginVO;
import com.fuhua.booknest.pojo.vo.UserProfileVO;

import java.util.List;

public interface UserService {
    User getUserById(String userId);
    void userRegister(UserRegisterDTO userRegisterDTO);
    User getUserByPhone(String userPhone);
    User getUserByEmail(String userEmail);
    UserLoginVO userLogin(UserLoginDTO userLoginDTO);

    /**
     * 更新用户资料（昵称 / 头像）
     * <p>「传 null 即不改」，两个都为 null 会直接报错（避免空 set 的 SQL 语法错误）。</p>
     * @param userId 目标用户ID（调用方从登录态取，不要用前端传的）
     * @param username 新昵称（可空）
     * @param avatar 新头像URL（可空）
     * @return 更新后的最新资料
     */
    UserProfileVO updateProfile(String userId, String username, String avatar);

    /**
     * 按昵称 / 账号模糊搜索用户（任命吧务时挑人用）
     * @param keyword 关键字（空白视为无匹配，不做全表扫描式返回）
     * @param limit 最多返回条数
     * @return 用户简要信息（不含手机号邮箱）
     */
    List<UserBriefVO> searchUsers(String keyword, int limit);
}
