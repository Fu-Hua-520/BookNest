package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booklist implements Serializable {
    private String id;
    private String userId;
    private String title;
    private String summary;
    private String coverImage;
    private Integer visibility;
    private Integer likeCount;
    private Integer collectCount;
    private Integer bookCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
