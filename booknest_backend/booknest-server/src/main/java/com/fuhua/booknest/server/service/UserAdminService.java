package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.vo.UserAdminVO;
import com.github.pagehelper.PageInfo;

/**
 * 管理后台用户服务
 */
public interface UserAdminService {

    /**
     * 分页查询用户列表（不含密码）
     * @param keyword 关键字（可空）
     * @param status 状态（可空）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    PageInfo<UserAdminVO> listUsers(String keyword, Integer status, Integer page, Integer pageSize);

    /**
     * 更新用户状态
     * @param userId 用户ID
     * @param status 状态（启用/禁用）
     */
    void updateUserStatus(String userId, Integer status);

    /**
     * 更新用户角色
     * @param userId 用户ID
     * @param role 角色（USER/ADMIN）
     */
    void updateUserRole(String userId, String role);
}
