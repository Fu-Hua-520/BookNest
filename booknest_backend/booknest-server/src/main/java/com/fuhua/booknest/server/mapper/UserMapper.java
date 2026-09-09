package com.fuhua.booknest.server.mapper;

import com.fuhua.booknest.pojo.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    /**
     * 根据电话查询用户
     * @param userPhone 手机号
     * @return 用户信息
     */
    @Select("select * from booknest.user where phone = #{userPhone}")
    User getUserByPhone(String userPhone);

    /**
     * 根据邮箱查询用户
     * @param userEmail 邮箱
     * @return 用户信息
     */
    @Select("select * from booknest.user where email = #{userEmail}")
    User getUserByEmail(String userEmail);

    /**
     * 根据账户编号查询用户
     * @param account 账户编号
     * @return 用户信息
     */
    @Select("select * from booknest.user where account = #{account}")
    User getUserByAccount(String account);

    /**
     * 根据用户ID查询用户
     * @param userId 用户ID
     * @return 用户信息
     */
    @Select("select * from booknest.user where id = #{userId}")
    User getUserById(String userId);

    /**
     * 插入新用户
     * @param user 用户信息
     */
    @Insert("insert into booknest.user(id, account, username, password, phone, email, avatar, user_level, status, role) " +
            "values (#{id}, #{account}, #{username}, #{password}, #{phone}, #{email}, #{avatar}, #{userLevel}, #{status}, #{role})")
    void insertUser(User user);
}
