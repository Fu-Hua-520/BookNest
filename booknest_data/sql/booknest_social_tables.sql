-- ====================================
-- BookNest 社交域建表
-- 涵盖：用户关注、系统通知、浏览历史
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
    `type` VARCHAR(20) NOT NULL COMMENT '通知类型：LIKE-点赞 COMMENT-评论 REPLY-回复评论 FOLLOW-关注 AUDIT-审核',
    `content` VARCHAR(500) NOT NULL COMMENT '通知内容',
    `source_id` VARCHAR(36) DEFAULT NULL COMMENT '关联资源ID（帖子ID等）',
    `anchor_id` VARCHAR(36) DEFAULT NULL COMMENT '锚点ID：REPLY 存评论ID，点击通知定位到该评论',
    `is_read` TINYINT DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `read_time` DATETIME DEFAULT NULL COMMENT '阅读时间',
    -- 消费幂等去重键（STORED 生成列，由 MySQL 自动维护，应用侧不写也不该写）。
    -- ⚠️ 为什么不直接对 (receiver_id, type, source_id, anchor_id) 建唯一键：
    --    MySQL 唯一索引逐列判等，而 NULL 之间互不相等，只要四列里有任何一列是 NULL，
    --    该行就永远不可能冲突 —— 键形同虚设。本项目 LIKE / COMMENT / FOLLOW 的
    --    anchor_id 恒为 NULL（FOLLOW 连 source_id 都是 NULL），正好全都落在这个盲区里。
    --    所以先把 NULL 折叠成空串再建键。分隔符 ':' 安全：四个组成部分只可能是
    --    UUID 或全大写常量，都不含 ':'。
    --    详见 booknest_data/sql/upgrade/2026-09-14-notification-dedup-upgrade.sql
    `dedup_key` VARCHAR(255)
        AS (CONCAT_WS(':', `receiver_id`, `type`, COALESCE(`source_id`, ''), COALESCE(`anchor_id`, ''))) STORED,

    -- 索引
    INDEX `idx_receiver` (`receiver_id`),
    INDEX `idx_receiver_read` (`receiver_id`, `is_read`),
    -- 消费幂等：MQ 是 at-least-once，同一条通知消息可能被投递两次。
    -- 消费端用 INSERT IGNORE，靠这个唯一键把「重复投递」变成影响行数 0 的正常跳过。
    -- 判定用业务四元组，不含 content / create_time —— 两次投递时间必然不同，
    -- 带上时间就永远判不出重复。
    UNIQUE KEY `uk_notification_dedup` (`dedup_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统通知表';

-- ------------------------------------------------------
-- 3. 浏览历史表
-- ------------------------------------------------------
-- (user_id, post_id) 唯一：同一个人反复看同一篇帖子只保留一行，
-- 只把 view_time 往后推、view_count 累加，历史列表才不会被同一篇帖子刷屏。
DROP TABLE IF EXISTS `user_browse_history`;
CREATE TABLE `user_browse_history` (
    `id` VARCHAR(36) NOT NULL COMMENT '记录ID（UUID）',
    `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
    `post_id` VARCHAR(36) NOT NULL COMMENT '帖子ID',
    `view_time` DATETIME DEFAULT NULL COMMENT '最后一次浏览时间',
    `view_count` INT NOT NULL DEFAULT 1 COMMENT '累计浏览次数',
    `create_time` DATETIME DEFAULT NULL COMMENT '首次浏览时间',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_post` (`user_id`, `post_id`),
    KEY `idx_user_time` (`user_id`, `view_time`),
    KEY `idx_post` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户浏览历史表';
