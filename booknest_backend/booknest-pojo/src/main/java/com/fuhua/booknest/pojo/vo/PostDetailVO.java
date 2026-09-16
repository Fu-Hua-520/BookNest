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
 * 帖子详情 VO（含正文全文与审核信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDetailVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String summary;
    private String coverImage;
    private String bookId;
    private String bookTitle;
    private String categoryId;
    private String categoryName;
    /** 帖子类型：NORMAL-普通 HELP-求助贴 */
    private String postType;
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

    // 正文（Markdown 全文）
    private String content;
    // 审核状态
    private Integer auditStatus;
    // 审核拒绝原因
    private String auditReason;
    /**
     * 帖子状态：1-已上架 0-草稿 3-已下架（被吧务隐藏）
     * 之前只给管理端用，前台看不出来；吧务要能隐藏/恢复吧内帖，详情页必须拿到这个值。
     */
    private Integer status;
}
