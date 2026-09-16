-- ====================================
-- BookNest 私信 / 群聊域建表
-- 涵盖：私信会话、私信与群聊消息、群聊、群成员
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
-- 1. 私信会话表（仅私聊；群聊见 chat_group）
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
-- 2. 群聊表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `chat_group`;
CREATE TABLE `chat_group` (
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
-- 3. 群成员表
-- ------------------------------------------------------
-- last_read_at 承担群聊的「已读位点」：群消息无法用单列 is_read 表达多人的已读态，
-- 改为每个成员各记一个时间点，未读数 = 该时间点之后、且不是自己发的消息条数。
DROP TABLE IF EXISTS `chat_group_member`;
CREATE TABLE `chat_group_member` (
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
-- 4. 消息表（私聊 + 群聊共用）
-- ------------------------------------------------------
-- conversation_id 对私聊存会话ID、对群聊存群ID，历史消息查询因此可以完全共用一条 SQL。
-- receiver_id 仅私聊有值：群消息接收方是多人，落到 group_id，receiver_id 置 NULL。
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id` VARCHAR(36) NOT NULL COMMENT '消息ID',
  `conversation_id` VARCHAR(36) NOT NULL COMMENT '渠道ID：私聊=会话ID，群聊=群ID',
  `sender_id` VARCHAR(36) NOT NULL COMMENT '发送者ID',
  `receiver_id` VARCHAR(36) DEFAULT NULL COMMENT '接收者ID（仅私聊；群聊为NULL）',
  `group_id` VARCHAR(36) DEFAULT NULL COMMENT '群ID（仅群聊；私聊为NULL）',
  `content` TEXT NOT NULL COMMENT '消息内容；msg_type=IMAGE 时为图片URL',
  `msg_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型：TEXT-文本 IMAGE-图片',
  `is_read` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读 0未读 1已读（仅私聊；群聊走 chat_group_member.last_read_at）',
  `create_time` DATETIME DEFAULT NULL COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation` (`conversation_id`),
  KEY `idx_group_time` (`group_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='私信/群聊消息表';

-- ------------------------------------------------------
-- 5. 群邀请 / 加入申请表（一表两用）
-- ------------------------------------------------------
-- type 区分方向：INVITE 邀请他人（等被邀请人同意） / JOIN_REQUEST 主动申请（等群主批准）。
-- 不再是「邀请即入群」：直接把人塞进陌生群既无法拒绝，也说不清是谁的主意。
-- invitee_id 在两种方向下都表示「要进群的那个人」，所以「我收到的邀请」与
-- 「我发起的申请」查的是同一列，只是 type 不同。
DROP TABLE IF EXISTS `chat_group_invitation`;
CREATE TABLE `chat_group_invitation` (
  `id` VARCHAR(36) NOT NULL COMMENT '记录ID',
  `group_id` VARCHAR(36) NOT NULL COMMENT '目标群ID',
  `inviter_id` VARCHAR(36) DEFAULT NULL COMMENT '发起人：INVITE 时是邀请人；JOIN_REQUEST 时为空',
  `invitee_id` VARCHAR(36) NOT NULL COMMENT '被邀请人 / 申请人',
  `type` VARCHAR(16) NOT NULL COMMENT 'INVITE-邀请他人 JOIN_REQUEST-申请加入',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING-待处理 ACCEPTED-已同意 REJECTED-已拒绝',
  `message` VARCHAR(200) DEFAULT NULL COMMENT '附言（邀请说明 / 申请理由）',
  `create_time` DATETIME DEFAULT NULL COMMENT '发起时间',
  `handle_time` DATETIME DEFAULT NULL COMMENT '处理时间，未处理时为空',
  PRIMARY KEY (`id`),
  KEY `idx_group_type_status` (`group_id`,`type`,`status`),
  KEY `idx_invitee_type_status` (`invitee_id`,`type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群邀请/加入申请表';
