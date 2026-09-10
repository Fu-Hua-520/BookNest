package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 书单详情 VO（含条目列表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BooklistDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String summary;
    private String coverImage;
    private String userId;
    private String userName;
    private String userAvatar;
    private Integer bookCount;
    private Integer likeCount;
    private Integer collectCount;
    private Integer visibility;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 条目列表
    private List<BooklistItemVO> items;
}
