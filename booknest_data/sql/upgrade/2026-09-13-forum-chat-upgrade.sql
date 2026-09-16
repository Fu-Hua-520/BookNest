-- ====================================
-- 升级脚本：论坛化改造（群聊 / 图片消息 / 浏览历史）
-- 目标库：已经初始化过、不想重建的 booknest 库
-- ====================================
--
-- 使用方式（在宿主机执行，密码取根目录 .env 的 DB_PASSWORD）：
--   docker exec -i booknest-mysql mysql -uroot -p"$DB_PASSWORD" booknest < booknest_data/sql/upgrade/2026-09-13-forum-chat-upgrade.sql
--
-- ⚠️ 本脚本是「一次性」的，不可重复执行：
--    MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS 之外的幂等写法，
--    重复执行会因列已存在而报 1060。若已执行过，请忽略报错或核对后再执行。
--
-- 为什么不直接把 ALTER 写进 booknest_chat_tables.sql：
--    那些文件挂在 /docker-entrypoint-initdb.d 下，只在数据目录为空（首次启动）时执行；
--    已经跑过初始化的库，改那些文件不会有任何效果。
--    本文件放在 sql/upgrade/ 子目录，官方 entrypoint 不会递归扫描，因此不会被自动执行。

SET NAMES utf8mb4;

USE booknest;

-- ------------------------------------------------------
-- 1. 消息表支持群聊与图片消息
-- ------------------------------------------------------
-- receiver_id 改为可空：群消息没有单一接收者
ALTER TABLE `chat_message`
    MODIFY COLUMN `receiver_id` VARCHAR(36) DEFAULT NULL COMMENT '接收者ID（仅私聊；群聊为NULL）';

-- group_id：群消息归属的群
ALTER TABLE `chat_message`
    ADD COLUMN `group_id` VARCHAR(36) DEFAULT NULL COMMENT '群ID（仅群聊；私聊为NULL）' AFTER `receiver_id`;

-- msg_type：文本 / 图片
ALTER TABLE `chat_message`
    ADD COLUMN `msg_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型：TEXT-文本 IMAGE-图片' AFTER `content`;

-- 群消息按 (群, 时间) 取历史
ALTER TABLE `chat_message`
    ADD KEY `idx_group_time` (`group_id`, `create_time`);

-- ------------------------------------------------------
-- 2. 群聊表
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_group` (
  `id` VARCHAR(36) NOT NULL COMMENT '群ID',
  `name` VARCHAR(64) NOT NULL COMMENT '群名称',
  `avatar` VARCHAR(512) DEFAULT NULL COMMENT '群头像（OSS URL）',
  `owner_id` VARCHAR(36) NOT NULL COMMENT '群主用户ID',
  `notice` VARCHAR(500) DEFAULT NULL COMMENT '群公告',
  `last_message` VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息预览',
  `last_msg_at` DATETIME DEFAULT NULL COMMENT '最后消息时间',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_last_msg_at` (`last_msg_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群聊表';

-- ------------------------------------------------------
-- 3. 群成员表（last_read_at 即群聊已读位点）
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `chat_group_member` (
  `id` VARCHAR(36) NOT NULL COMMENT '成员关系ID',
  `group_id` VARCHAR(36) NOT NULL COMMENT '群ID',
  `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
  `role` VARCHAR(16) NOT NULL DEFAULT 'MEMBER' COMMENT '角色：OWNER-群主 MEMBER-普通成员',
  `last_read_at` DATETIME DEFAULT NULL COMMENT '已读位点，为空表示从未读过',
  `join_time` DATETIME DEFAULT NULL COMMENT '入群时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_id`,`user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群成员表';

-- ------------------------------------------------------
-- 4. 浏览历史表
-- ------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_browse_history` (
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

-- ------------------------------------------------------
-- 5. 校验
-- ------------------------------------------------------
SELECT column_name, column_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'booknest' AND table_name = 'chat_message'
ORDER BY ordinal_position;

SELECT table_name FROM information_schema.tables
WHERE table_schema = 'booknest'
  AND table_name IN ('chat_group', 'chat_group_member', 'user_browse_history');
