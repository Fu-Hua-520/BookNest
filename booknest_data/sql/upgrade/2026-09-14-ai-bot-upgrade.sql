-- =========================================================
-- 2026-09-14 评论区 AI 机器人（替换旧「AI 助手」模块）
--
-- 背景：旧 AI 助手是平台自带的全局对话助手，Key 与模型写死在 application.yml，
--       与本次需求（用户自建机器人 + 自带 Key + 管理员审核 + 评论区 @ 触发）完全不同，
--       因此整体下线，并把它的两张表一并删掉。
--
-- 变更：
--   1) DROP ai_message / ai_conversation  —— 旧助手会话数据，模块已删除，无消费方
--   2) CREATE ai_bot                     —— 机器人配置表
--   3) post_comment 新增 bot_id 并放开 user_id 的非空约束
--      （AI 回复也是一种评论，走同一张表最省事：楼中楼、删除递归、通知都能直接复用；
--        机器人没有对应的 user 行，所以 user_id 必须允许为 NULL）
--
-- 执行方式（本脚本不在 docker-entrypoint-initdb.d 扫描范围内，需手动跑）：
--   docker cp 2026-09-14-ai-bot-upgrade.sql booknest-mysql:/tmp/u.sql
--   docker exec booknest-mysql sh -c \
--     "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD booknest < /tmp/u.sql"
--   跑完删掉容器内脚本，避免重复执行：docker exec booknest-mysql rm -f /tmp/u.sql
--
-- ⚠️ 重复执行会报 1060 Duplicate column name: bot_id / 1091 等，属预期（列已存在）。
--    若只想重复执行的后半段，把前面几条注释掉即可。
-- =========================================================
SET NAMES utf8mb4;

-- 1. 旧 AI 助手模块的表整体下线
DROP TABLE IF EXISTS `ai_message`;
DROP TABLE IF EXISTS `ai_conversation`;

-- 2. 评论区 AI 机器人配置表
CREATE TABLE IF NOT EXISTS `ai_bot` (
  `id` VARCHAR(36) NOT NULL COMMENT '机器人ID（UUID）',
  `owner_id` VARCHAR(36) NOT NULL COMMENT '创建者用户ID',
  `name` VARCHAR(30) NOT NULL COMMENT '机器人名称，同时也是 @ 触发词，全站唯一',
  `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像URL（OSS）',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '简介，列表里展示',
  `provider` VARCHAR(20) NOT NULL COMMENT '模型厂商：deepseek / dashscope',
  `base_url` VARCHAR(200) NOT NULL COMMENT 'OpenAI 兼容 base-url（不含结尾 /v1，由 SDK 自己拼）',
  `model` VARCHAR(100) NOT NULL COMMENT '模型名，如 deepseek-flash / qwen-max',
  `api_key` VARCHAR(200) NOT NULL COMMENT '机器人自己的 API Key（对外一律脱敏）',
  `system_prompt` VARCHAR(2000) DEFAULT NULL COMMENT '系统提示词：设定 AI 的身份、语气、回答范围',
  `temperature` DECIMAL(3,2) DEFAULT 0.70 COMMENT '采样温度 0.00~2.00',
  `max_tokens` INT DEFAULT 800 COMMENT '单条回复的最大 token 数',
  `audit_status` TINYINT DEFAULT 0 COMMENT '审核状态 0待审核 1已通过 2已驳回',
  `reject_reason` VARCHAR(200) DEFAULT NULL COMMENT '驳回原因',
  `enabled` TINYINT DEFAULT 1 COMMENT '创建者侧开关 1启用 0停用',
  `reply_count` INT DEFAULT 0 COMMENT '累计回复条数',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bot_name` (`name`),
  KEY `idx_bot_owner` (`owner_id`),
  KEY `idx_bot_audit` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论区AI机器人配置表';

-- 3. post_comment 承接 AI 回复
ALTER TABLE `post_comment`
    ADD COLUMN `bot_id` VARCHAR(36) DEFAULT NULL COMMENT 'AI 机器人ID（真人评论时为 NULL）' AFTER `user_id`;

ALTER TABLE `post_comment`
    MODIFY COLUMN `user_id` VARCHAR(36) DEFAULT NULL COMMENT '评论者用户ID（AI 机器人回复时为 NULL）';

ALTER TABLE `post_comment`
    ADD INDEX `idx_comment_bot` (`bot_id`);
