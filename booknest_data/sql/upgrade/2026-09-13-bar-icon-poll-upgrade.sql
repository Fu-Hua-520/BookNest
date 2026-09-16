-- ====================================
-- 升级脚本：书吧去分级 + 吧名加「吧」+ 书吧图标 + 帖子投票 PK
-- 目标库：已经初始化过、不想重建的 booknest 库
-- ====================================
--
-- 覆盖本轮四项改造：
--   1. category 取消两级结构 —— 所有吧拍平成一级（parent_id 一律置 NULL）
--   2. category 吧名统一加「吧」后缀（文学 → 文学吧、小说 → 小说吧……）
--   3. category 增加 icon，用于书吧图标（为空时前端用吧名首字兜底）
--   4. 新建 post_poll / post_poll_vote，承载帖子里的 A/B 投票 PK
--
-- 使用方式（在宿主机执行，先 cd 到项目根 D:\FuHua520\booknest）：
--   docker cp booknest_data/sql/upgrade/2026-09-13-bar-icon-poll-upgrade.sql booknest-mysql:/tmp/u.sql
--   docker exec booknest-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 booknest < /tmp/u.sql'
--
-- ⚠️ 执行前先确认容器在跑：docker ps 里必须能看到 booknest-mysql 为 Up。
--    compose 里 mysql 是 `restart: no`，Docker Desktop 重启后容器是 Exited 状态。
--    docker exec 对已停止容器会报 "container ... is not running"；
--    而 docker cp 对已停止容器**不报错**（直接写进容器可写层），所以很容易误判成"第一条成功了"。
--
-- ⚠️ 密码变量名：容器内注入的是 MYSQL_ROOT_PASSWORD（见 docker-compose.yml），
--    不是 DB_PASSWORD。DB_PASSWORD 只存在于宿主机的 .env 里，容器内取不到，
--    写成 -p"$DB_PASSWORD" 会展开成空密码 → ERROR 1045 Access denied (using password: NO)。
--
-- ⚠️ PowerShell 里不能用 `<` 重定向（会报「< 无法识别为 cmdlet」），改用上面的 docker cp 两步法。
--
-- ⚠️ 第 3 步的 ALTER 不可重复执行（MySQL 8 没有 ADD COLUMN IF NOT EXISTS）：
--    重复执行会报 1060 Duplicate column name 'icon'。前两步的 UPDATE 与第 4 步的
--    CREATE TABLE IF NOT EXISTS 都是幂等的，重跑无害。

SET NAMES utf8mb4;

USE booknest;

-- ------------------------------------------------------
-- 1. 书吧去分级：所有吧拍平为一级
-- ------------------------------------------------------
-- 前台已不再有「分类」概念，一个吧就是一条记录。历史数据的 24 个二级分类
-- 直接升级成独立的一级吧（散文吧、诗歌吧……），不再挂在父吧下面。
UPDATE `category` SET `parent_id` = NULL WHERE `parent_id` IS NOT NULL;

-- ------------------------------------------------------
-- 2. 吧名统一加「吧」后缀
-- ------------------------------------------------------
-- 幂等：已经以「吧」结尾的不会被重复追加。
-- CHAR_LENGTH 上限保护：name 是 VARCHAR(50)，超长的名字跳过而不是截断报错。
UPDATE `category`
   SET `name` = CONCAT(`name`, '吧')
 WHERE RIGHT(`name`, 1) <> '吧'
   AND CHAR_LENGTH(`name`) <= 47;

-- ------------------------------------------------------
-- 3. 书吧图标
-- ------------------------------------------------------
-- 只存 OSS 上的 URL；为 NULL 时前端回退成吧名首字（单字方块）。
ALTER TABLE `category`
    ADD COLUMN `icon` VARCHAR(500) DEFAULT NULL COMMENT '书吧图标URL（为空时前端用吧名首字兜底）' AFTER `name`;

-- ------------------------------------------------------
-- 4. 帖子投票 PK（A/B 两个词条对抗）
-- ------------------------------------------------------
-- 一篇帖子最多一个投票，用 uk_post_id 兜住。
-- end_time 到达后不再接受投票（后端判定，不依赖前端）。
CREATE TABLE IF NOT EXISTS `post_poll` (
  `id` VARCHAR(36) NOT NULL COMMENT '投票ID',
  `post_id` VARCHAR(36) NOT NULL COMMENT '所属帖子ID',
  `option_a` VARCHAR(50) NOT NULL COMMENT 'A 方词条',
  `option_b` VARCHAR(50) NOT NULL COMMENT 'B 方词条',
  `end_time` DATETIME NOT NULL COMMENT '投票截止时间',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子投票（PK）表';

-- 一人一票：uk_poll_user 是并发下的唯一防线（应用层先查后插会有竞态）。
-- 不开放改票，投错了不能反悔，所以没有 update 路径。
CREATE TABLE IF NOT EXISTS `post_poll_vote` (
  `id` VARCHAR(36) NOT NULL COMMENT '投票记录ID',
  `poll_id` VARCHAR(36) NOT NULL COMMENT '投票ID',
  `user_id` VARCHAR(36) NOT NULL COMMENT '投票人ID',
  `choice` VARCHAR(8) NOT NULL COMMENT '选择：A 或 B',
  `create_time` DATETIME DEFAULT NULL COMMENT '投票时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_poll_user` (`poll_id`,`user_id`),
  KEY `idx_poll_choice` (`poll_id`,`choice`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子投票记录表';

-- ------------------------------------------------------
-- 5. 校验
-- ------------------------------------------------------
-- 5.1 必须没有任何 parent_id 非空的行（去分级是否彻底）
SELECT 'remaining_children' AS check_item,
       count(*) AS cnt
FROM `category` WHERE `parent_id` IS NOT NULL;

-- 5.2 吧名列表（应当全部以「吧」结尾）
SELECT `id`, `name`, `icon`, `sort_order`, `status`, `audit_status`
FROM `category` ORDER BY `sort_order`, `name`;

-- 5.3 新列 / 新表是否就位
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'booknest' AND table_name = 'category' AND column_name = 'icon';

SELECT table_name FROM information_schema.tables
WHERE table_schema = 'booknest' AND table_name IN ('post_poll','post_poll_vote')
ORDER BY table_name;
