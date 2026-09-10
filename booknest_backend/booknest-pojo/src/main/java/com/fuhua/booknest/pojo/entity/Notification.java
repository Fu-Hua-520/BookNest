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
public class Notification implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String receiverId;
    private String type;
    private String content;
    private String sourceId;
    private Integer isRead;
    private LocalDateTime createTime;
    private LocalDateTime readTime;
}
