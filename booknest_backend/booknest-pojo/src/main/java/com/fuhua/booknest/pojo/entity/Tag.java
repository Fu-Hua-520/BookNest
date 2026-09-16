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
public class Tag implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    /**
     * 被多少篇帖子引用。
     * 注意：该值由 post_tag 关联表实时统计得出（见 TagMapper.xml），
     * 不是 tag 表 use_count 列的原始存储值 —— 该列已降级为遗留列。
     */
    private Integer useCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
