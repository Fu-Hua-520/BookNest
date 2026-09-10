package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创建书单请求 DTO
 */
@Data
public class BooklistCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 书单标题
    @NotBlank(message = "标题不能为空")
    private String title;

    // 书单简介
    private String summary;

    // 封面图URL（可空）
    private String coverImage;

    // 可见性（0公开 1私密，可空默认0）
    private Integer visibility;

    // 初始条目列表（可空）
    private List<BooklistItemDTO> items;
}
