-- ====================================
-- BookNest 评论区 AI 机器人域建表
-- 涵盖：ai_bot（用户自建、管理员审核的评论区 AI 机器人）
--
-- 与旧「AI 助手」模块的区别：
--   旧模块是平台提供的一套全局对话助手（ai_conversation / ai_message 两张表 + 服务端固定 Key）。
--   新模块是「用户自带 Key」的评论区机器人：每个机器人有自己的厂商 / 模型 / API Key /
--   系统提示词，需管理员审核后才可被 @ 触发，因此只需要一张配置表。
--   AI 的回复本身不落在这里，而是以 bot_id 写进 post_comment，复用评论区的楼中楼与通知。
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
-- 1. 评论区 AI 机器人表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `ai_bot`;
CREATE TABLE `ai_bot` (
  `id` VARCHAR(36) NOT NULL COMMENT '机器人ID（UUID）',
  `owner_id` VARCHAR(36) NOT NULL COMMENT '创建者用户ID',
  `name` VARCHAR(30) NOT NULL COMMENT '机器人名称，同时也是 @ 触发词，全站唯一',
  `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL（OSS）',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '简介，列表里展示',
  `provider` VARCHAR(20) NOT NULL COMMENT '模型厂商：deepseek / dashscope',
  `base_url` VARCHAR(200) NOT NULL COMMENT 'OpenAI 兼容 base-url（不含结尾 /v1，由 SDK 自己拼）',
  `model` VARCHAR(100) NOT NULL COMMENT '模型名，如 deepseek-flash / qwen-max',
  `api_key` VARCHAR(200) NOT NULL COMMENT '机器人自己的 API Key（仅创建者与管理员可见，对外一律脱敏）',
  `system_prompt` VARCHAR(2000) DEFAULT NULL COMMENT '系统提示词：设定 AI 的身份、语气、回答范围',
  `temperature` DECIMAL(3,2) DEFAULT 0.70 COMMENT '采样温度 0.00~2.00',
  `max_tokens` INT DEFAULT 800 COMMENT '单条回复的最大 token 数',
  `audit_status` TINYINT DEFAULT 0 COMMENT '审核状态 0待审核 1已通过 2已驳回',
  `reject_reason` VARCHAR(200) DEFAULT NULL COMMENT '驳回原因',
  `enabled` TINYINT DEFAULT 1 COMMENT '创建者侧开关 1启用 0停用（停用后不再被 @ 触发）',
  `reply_count` INT DEFAULT 0 COMMENT '累计回复条数（触发成功 +1）',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',

  PRIMARY KEY (`id`),
  -- 触发词靠名字精确匹配，重名会让 @ 产生歧义，必须唯一
  UNIQUE KEY `uk_bot_name` (`name`),
  KEY `idx_bot_owner` (`owner_id`),
  KEY `idx_bot_audit` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论区AI机器人配置表';
