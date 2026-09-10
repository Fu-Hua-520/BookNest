package com.fuhua.booknest.pojo.vo;

import com.fuhua.booknest.pojo.entity.Book;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 书单条目 VO（含完整书籍信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BooklistItemVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String bookId;
    private Book book;
    private String note;
    private Integer sortOrder;
}
