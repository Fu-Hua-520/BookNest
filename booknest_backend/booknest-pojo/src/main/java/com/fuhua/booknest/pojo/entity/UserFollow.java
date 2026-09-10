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
public class UserFollow implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String followerId;
    private String followeeId;
    private LocalDateTime createTime;
}
