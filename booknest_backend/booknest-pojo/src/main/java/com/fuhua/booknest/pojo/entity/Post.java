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
public class Post implements Serializable {
    private String id;
    private String userId;
    private String bookId;
    private String title;
    private String summary;
    private String contentUrl;
    private String coverImage;
    private String categoryId;
    private Long viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer collectCount;
    private Integer status;
    private Integer auditStatus;
    private String auditReason;
    private LocalDateTime auditTime;
    private Integer isTop;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private LocalDateTime publishTime;
}
