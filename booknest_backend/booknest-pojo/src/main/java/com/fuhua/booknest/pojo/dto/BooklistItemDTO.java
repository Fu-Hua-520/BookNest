package com.fuhua.booknest.pojo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 书单初始条目 DTO（创建书单时携带）
 */
@Data
public class BooklistItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 书籍ID
    @NotBlank(message = "书籍ID不能为空")
    private String bookId;

    // 条目备注
    private String note;

    // 排序号（可空，默认按顺序 1,2,3...）
    private Integer sortOrder;
}
