package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 书吧等级称号
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarLevelTitleVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 等级（1~10） */
    private Integer level;
    /** 称号名 */
    private String title;
}
