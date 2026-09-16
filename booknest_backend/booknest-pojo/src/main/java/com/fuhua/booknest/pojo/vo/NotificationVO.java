package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String type;
    private String content;
    private String sourceId;
    // 锚点ID：REPLY 存评论 ID，前端打开帖子后滚动并高亮该评论
    private String anchorId;
    private Integer isRead;
    private LocalDateTime createTime;
}
