package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 浏览历史 VO（帖子信息在 SQL 里一次性 join 出来，避免逐条回查造成 N+1）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowseHistoryVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String postId;
    private String title;
    private String summary;
    private String coverImage;
    private String categoryId;
    private String categoryName;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private Long viewCount;
    private Integer likeCount;
    private Integer commentCount;
    /** 我最后一次浏览该帖的时间 */
    private LocalDateTime viewTime;
    /** 我累计浏览该帖的次数 */
    private Integer myViewCount;
}
