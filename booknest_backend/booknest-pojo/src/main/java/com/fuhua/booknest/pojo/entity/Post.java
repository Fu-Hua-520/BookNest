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
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String bookId;
    private String title;
    private String summary;
    private String contentUrl;
    private String coverImage;
    private String categoryId;
    /** 帖子类型：NORMAL-普通 HELP-求助贴（见 PostTypeConstant） */
    private String postType;
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
