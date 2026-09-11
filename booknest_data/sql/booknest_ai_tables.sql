-- ====================================
-- BookNest AI 助手域建表
-- 涵盖：AI 会话、AI 消息
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
-- 1. AI 会话表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `ai_conversation`;
CREATE TABLE `ai_conversation` (
  `id` VARCHAR(36) NOT NULL COMMENT '会话ID',
  `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
  `title` VARCHAR(100) DEFAULT NULL COMMENT '会话标题（LLM自动生成）',
  `model` VARCHAR(50) DEFAULT NULL COMMENT '使用的模型',
  `message_count` INT DEFAULT 0 COMMENT '消息数',
  `last_message_preview` VARCHAR(200) DEFAULT NULL COMMENT '最后一条消息预览',
  `is_pinned` TINYINT DEFAULT 0 COMMENT '是否置顶 0否1是',
  `is_archived` TINYINT DEFAULT 0 COMMENT '是否归档 0否1是',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1正常0删除',
  `create_time` DATETIME DEFAULT NULL,
  `update_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI会话表';

-- ------------------------------------------------------
-- 2. AI 消息表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id` VARCHAR(36) NOT NULL COMMENT '消息ID',
  `conversation_id` VARCHAR(36) NOT NULL COMMENT '会话ID',
  `role` VARCHAR(20) NOT NULL COMMENT '角色 user/assistant/system',
  `content` TEXT COMMENT '消息内容(Markdown)',
  `token_count` INT DEFAULT NULL COMMENT 'token数',
  `tool_calls` TEXT COMMENT '工具调用JSON',
  `recommendations` TEXT COMMENT '推荐结果JSON',
  `model` VARCHAR(50) DEFAULT NULL COMMENT '使用的模型',
  `is_error` TINYINT DEFAULT 0 COMMENT '是否出错 0否1是',
  `create_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI消息表';
