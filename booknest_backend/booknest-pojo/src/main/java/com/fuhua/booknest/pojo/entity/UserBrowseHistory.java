package com.fuhua.booknest.pojo.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 浏览历史实体
 *
 * 以 (user_id, post_id) 唯一：同一个人反复看同一篇帖子只保留一行，
 * 只把 view_time 往后推，避免历史列表被同一篇帖子刷屏。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBrowseHistory implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String userId;
    private String postId;
    /** 最后一次浏览时间 */
    private LocalDateTime viewTime;
    /** 累计浏览次数 */
    private Integer viewCount;
    private LocalDateTime createTime;
}
