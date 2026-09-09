package com.fuhua.booknest.pojo.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserRegisterDTO implements Serializable {
    private String phone;
    private String email;
    private String password;
}
