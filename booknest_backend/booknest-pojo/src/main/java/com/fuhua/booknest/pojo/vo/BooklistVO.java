package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 书单卡片 VO（列表/摘要展示用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BooklistVO implements Serializable {
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
}
