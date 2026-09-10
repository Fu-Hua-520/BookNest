package com.fuhua.booknest.pojo.vo;

import com.fuhua.booknest.pojo.entity.Tag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子卡片 VO（列表/摘要展示用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String summary;
    private String coverImage;
    private String bookId;
    private String bookTitle;
    private String categoryId;
    private String categoryName;
    private String authorId;
    private String authorName;
    private String authorAvatar;
    private List<Tag> tags;
    private Long viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer collectCount;
    private Integer isTop;
    private LocalDateTime publishTime;
}
