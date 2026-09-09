package com.fuhua.booknest.server.service;

import com.fuhua.booknest.pojo.dto.UserLoginDTO;
import com.fuhua.booknest.pojo.dto.UserRegisterDTO;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.UserLoginVO;

public interface UserService {
    User getUserById(String userId);
    void userRegister(UserRegisterDTO userRegisterDTO);
    User getUserByPhone(String userPhone);
    User getUserByEmail(String userEmail);
    UserLoginVO userLogin(UserLoginDTO userLoginDTO);
}
