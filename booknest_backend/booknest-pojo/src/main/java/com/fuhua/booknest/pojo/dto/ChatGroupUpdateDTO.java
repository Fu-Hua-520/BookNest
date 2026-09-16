package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改群资料 DTO（群主可用）
 */
@Data
public class ChatGroupUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Size(max = 30, message = "群名称不超过 30 个字")
    private String name;

    @Size(max = 200, message = "群公告不超过 200 个字")
    private String notice;

    private String avatar;
}
