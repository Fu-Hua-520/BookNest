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
    private Integer isRead;
    private LocalDateTime createTime;
}
