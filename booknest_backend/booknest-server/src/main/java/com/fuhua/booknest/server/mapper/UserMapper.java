package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.UserAdminVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    /**
     * 根据电话查询用户
     * @param userPhone 手机号
     * @return 用户信息
     */
    User getUserByPhone(@Param("userPhone") String userPhone);

    /**
     * 根据邮箱查询用户
     * @param userEmail 邮箱
     * @return 用户信息
     */
    User getUserByEmail(@Param("userEmail") String userEmail);

    /**
     * 根据账户编号查询用户
     * @param account 账户编号
     * @return 用户信息
     */
    User getUserByAccount(@Param("account") String account);

    /**
     * 根据用户ID查询用户
     * @param userId 用户ID
     * @return 用户信息
     */
    User getUserById(@Param("userId") String userId);

    /**
     * 插入新用户
     * @param user 用户信息
     */
    void insertUser(User user);

    /**
     * 管理后台：关键字/状态条件查询用户列表（不含密码，供 PageHelper 分页）
     * @param keyword 关键字（用户名/账户/邮箱模糊匹配，可空）
     * @param status 状态（可空）
     * @return 用户列表
     */
    List<UserAdminVO> list(@Param("keyword") String keyword,
                           @Param("status") Integer status);

    /**
     * 更新用户状态
     * @param id 用户ID
     * @param status 状态
     */
    void updateStatus(@Param("id") String id, @Param("status") Integer status);

    /**
     * 更新用户角色
     * @param id 用户ID
     * @param role 角色
     */
    void updateRole(@Param("id") String id, @Param("role") String role);
}
