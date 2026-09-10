package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.constant.MessageConstant;
import com.fuhua.booknest.common.constant.StatusConstant;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.UserAdminVO;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.UserAdminService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class UserAdminServiceImpl implements UserAdminService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public PageInfo<UserAdminVO> listUsers(String keyword, Integer status, Integer page, Integer pageSize) {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }

        PageHelper.startPage(page, pageSize);
        List<UserAdminVO> list = userMapper.list(keyword, status);
        return new PageInfo<>(list);
    }

    @Override
    public void updateUserStatus(String userId, Integer status) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        if (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new BaseException("状态非法");
        }
        userMapper.updateStatus(userId, status);
    }

    @Override
    public void updateUserRole(String userId, String role) {
        User user = userMapper.getUserById(userId);
        if (user == null) {
            throw new BaseException("用户不存在");
        }
        if (!MessageConstant.ROLE_USER.equals(role) && !MessageConstant.ROLE_ADMIN.equals(role)) {
            throw new BaseException("角色非法");
        }
        userMapper.updateRole(userId, role);
    }
}
