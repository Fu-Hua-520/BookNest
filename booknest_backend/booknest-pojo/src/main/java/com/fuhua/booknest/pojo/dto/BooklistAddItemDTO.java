package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 向书单添加条目请求 DTO
 */
@Data
public class BooklistAddItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 书籍ID
    @NotBlank(message = "书籍ID不能为空")
    private String bookId;

    // 条目备注
    private String note;
}
