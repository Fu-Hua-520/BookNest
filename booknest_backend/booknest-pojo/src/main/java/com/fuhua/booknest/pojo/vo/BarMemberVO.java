package com.fuhua.booknest.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 我在某个书吧里的身份与等级
 *
 * <p>role 取值见服务端 BarRoleConstant：OWNER / MODERATOR / MEMBER / NONE。
 * 前端据此决定要不要显示「吧务」入口。</p>
 *
 * <p>title 可能为 null —— 吧主没给这个等级设称号时，前端回退显示 Lv.N。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarMemberVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String barId;
    private String userId;
    private String username;
    private String avatar;
    /** 是否关注（关注即入吧） */
    private Boolean followed;
    /** 角色：OWNER / MODERATOR / MEMBER / NONE */
    private String role;
    /** 吧内累计经验 */
    private Integer exp;
    /** 吧内等级 */
    private Integer level;
    /** 本等级称号（可能为 null） */
    private String title;
    /** 今日已获得经验 */
    private Integer dailyExp;
    /** 每日经验上限 */
    private Integer dailyLimit;
    /** 升到下一级还需多少经验（已满级为 null） */
    private Integer nextLevelExp;
}
