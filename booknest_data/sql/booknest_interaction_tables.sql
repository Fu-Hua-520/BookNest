-- ====================================
-- BookNest 帖子互动域建表
-- 涵盖：帖子评论、评论点赞、帖子点赞、帖子收藏
-- ====================================

-- 强制客户端连接字符集为 utf8mb4（必须放在文件最前）
-- 容器内 mysql 客户端在 LANG 未设置时默认使用 latin1，会把脚本里的中文
-- 按 cp1252 误解后再编码为 utf8mb4，形成永久性双重编码乱码。
-- 详见 docker-compose.yml 中 mysql 服务的 LANG 说明。
SET NAMES utf8mb4;

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS booknest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE booknest;

-- ------------------------------------------------------
-- 1. 帖子评论表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `post_comment`;
CREATE TABLE `post_comment` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '评论ID（UUID）',
    `post_id` VARCHAR(36) NOT NULL COMMENT '帖子ID',
    `user_id` VARCHAR(36) NOT NULL COMMENT '评论者用户ID',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `reply_id` VARCHAR(36) DEFAULT NULL COMMENT '被回复的评论ID，顶级评论为NULL',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    INDEX `idx_post_id` (`post_id`),
    INDEX `idx_reply_id` (`reply_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子评论表';

-- ------------------------------------------------------
-- 2. 评论点赞表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `post_comment_like`;
CREATE TABLE `post_comment_like` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '点赞记录ID（UUID）',
    `comment_id` VARCHAR(36) NOT NULL COMMENT '评论ID',
    `user_id` VARCHAR(36) NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

-- ------------------------------------------------------
-- 3. 帖子点赞表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `post_like`;
CREATE TABLE `post_like` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '点赞记录ID（UUID）',
    `post_id` VARCHAR(36) NOT NULL COMMENT '帖子ID',
    `user_id` VARCHAR(36) NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';

-- ------------------------------------------------------
-- 4. 帖子收藏表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `post_collect`;
CREATE TABLE `post_collect` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '收藏记录ID（UUID）',
    `post_id` VARCHAR(36) NOT NULL COMMENT '帖子ID',
    `user_id` VARCHAR(36) NOT NULL COMMENT '收藏用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子收藏表';
