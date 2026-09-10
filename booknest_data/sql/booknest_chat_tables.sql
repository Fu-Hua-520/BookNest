-- ====================================
-- BookNest 私信域建表
-- 涵盖：私信会话、私信消息
-- ====================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS booknest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE booknest;

-- ------------------------------------------------------
-- 1. 私信会话表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `chat_conversation`;
CREATE TABLE `chat_conversation` (
  `id` VARCHAR(36) NOT NULL COMMENT '会话ID',
  `user1_id` VARCHAR(36) NOT NULL COMMENT '用户1ID（字典序较小者）',
  `user2_id` VARCHAR(36) NOT NULL COMMENT '用户2ID（字典序较大者）',
  `last_message` VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息预览',
  `last_msg_at` DATETIME DEFAULT NULL COMMENT '最后消息时间',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_pair` (`user1_id`,`user2_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信会话表';

-- ------------------------------------------------------
-- 2. 私信消息表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id` VARCHAR(36) NOT NULL COMMENT '消息ID',
  `conversation_id` VARCHAR(36) NOT NULL COMMENT '会话ID',
  `sender_id` VARCHAR(36) NOT NULL COMMENT '发送者ID',
  `receiver_id` VARCHAR(36) NOT NULL COMMENT '接收者ID',
  `content` TEXT NOT NULL COMMENT '消息内容',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读 0未读 1已读',
  `create_time` DATETIME DEFAULT NULL COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信消息表';
