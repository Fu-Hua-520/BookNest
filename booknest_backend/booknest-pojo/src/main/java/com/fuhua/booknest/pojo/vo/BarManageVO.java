package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 管理后台「书吧吧务」视图：一个吧的吧主 + 管理员 + 等级称号
 *
 * <p>为什么单独出一个 VO 而不是让前端分别调三个接口：管理端改吧务时，
 * 吧主一旦换人，管理员列表就得跟着刷新（原吧主可能同时是管理员），
 * 分三次请求很容易出现界面上三个部分对不上的中间态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarManageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String barId;
    private String barName;
    /** 书吧图标 URL，为空时前端用吧名首字兜底 */
    private String icon;
    /** 吧主用户ID；为 null 表示官方吧（无人担任吧主） */
    private String ownerId;
    private String ownerName;
    private String ownerAvatar;
    /** 吧务（不含吧主） */
    private List<BarModeratorVO> moderators;
    /** 等级称号（只含设过称号的等级） */
    private List<BarLevelTitleVO> titles;
    /** 吧内成员数，给管理员一个规模参照 */
    private Long memberCount;
}
