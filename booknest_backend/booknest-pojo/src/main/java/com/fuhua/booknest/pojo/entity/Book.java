package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Book implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String author;
    private String isbn;
    private String coverUrl;
    private String description;
    private String publisher;
    private String publishDate;
    private BigDecimal rating;
    private Integer ratingCount;
    private String source;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
