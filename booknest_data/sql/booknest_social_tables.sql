-- ====================================
-- BookNest 社交域建表
-- 涵盖：用户关注、系统通知
-- ====================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS booknest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE booknest;

-- ------------------------------------------------------
-- 1. 用户关注表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `user_follow`;
CREATE TABLE `user_follow` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '关注记录ID（UUID）',
    `follower_id` VARCHAR(36) NOT NULL COMMENT '关注者用户ID',
    `followee_id` VARCHAR(36) NOT NULL COMMENT '被关注者用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',

    -- 唯一约束：防止重复关注
    UNIQUE KEY `uk_follower_followee` (`follower_id`, `followee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户关注表';

-- ------------------------------------------------------
-- 2. 系统通知表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `notification`;
CREATE TABLE `notification` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知ID',
    `receiver_id` VARCHAR(36) NOT NULL COMMENT '接收者用户ID',
    `type` VARCHAR(20) NOT NULL COMMENT '通知类型：LIKE-点赞 COMMENT-评论 FOLLOW-关注 AUDIT-审核',
    `content` VARCHAR(500) NOT NULL COMMENT '通知内容',
    `source_id` VARCHAR(36) DEFAULT NULL COMMENT '关联资源ID（帖子ID等）',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',

    -- 索引
    INDEX `idx_receiver` (`receiver_id`),
    INDEX `idx_receiver_read` (`receiver_id`, `is_read`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';
