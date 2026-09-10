package com.fuhua.booknest.common.constant;

/**
 * 帖子状态与审核状态常量，供前后端与各模块统一使用
 */
public class PostStatusConstant {

    // 帖子状态：草稿
    public static final Integer STATUS_DRAFT = 0;
    // 帖子状态：已发布
    public static final Integer STATUS_PUBLISHED = 1;
    // 帖子状态：已下架
    public static final Integer STATUS_OFFLINE = 3;

    // 审核状态：待审核
    public static final Integer AUDIT_PENDING = 0;
    // 审核状态：审核通过
    public static final Integer AUDIT_APPROVED = 1;
    // 审核状态：审核拒绝
    public static final Integer AUDIT_REJECTED = 2;
}
