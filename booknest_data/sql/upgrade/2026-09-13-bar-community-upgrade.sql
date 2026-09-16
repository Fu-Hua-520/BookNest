-- ====================================
-- 升级脚本：移除投票 PK + 书吧社区化（吧务 / 关注 / 等级 / 称号）
-- 目标库：已经初始化过、不想重建的 booknest 库
-- ====================================
--
-- 本轮覆盖：
--   1. 移除上一轮加的帖子投票 PK（post_poll / post_poll_vote）
--   2. bar_moderator    —— 吧主任命的管理员
--   3. bar_member       —— 关注书吧即入吧，同时承载吧内等级与经验
--   4. bar_level_title  —— 吧主自定义的等级称号（未设置时前端只显示 Lv.N）
--
-- 使用方式（在宿主机执行，先 cd 到项目根 D:\FuHua520\booknest）：
--   docker cp booknest_data/sql/upgrade/2026-09-13-bar-community-upgrade.sql booknest-mysql:/tmp/u.sql
--   docker exec booknest-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 booknest < /tmp/u.sql'
--
-- ⚠️ 执行前先确认容器在跑：`docker ps` 里必须能看到 booknest-mysql 为 Up。
--    compose 里 mysql 是 `restart: no`，Docker Desktop 重启后容器是 Exited 状态。
--    docker exec 对已停止容器会报 "container ... is not running"；
--    而 docker cp 对已停止容器**不报错**（直接写进容器可写层），所以很容易误判成"第一条成功了"。
-- ⚠️ 容器内密码变量名是 MYSQL_ROOT_PASSWORD（不是 DB_PASSWORD，后者只在宿主机 .env 里）。
-- ⚠️ 全部语句都是 IF EXISTS / IF NOT EXISTS，可安全重复执行。
-- ⚠️ 第 1 步会**真的删掉**两张投票表及其中数据；功能已整体下线，无需保留。

SET NAMES utf8mb4;

USE booknest;

-- ------------------------------------------------------
-- 1. 移除投票 PK
-- ------------------------------------------------------
DROP TABLE IF EXISTS `post_poll_vote`;
DROP TABLE IF EXISTS `post_poll`;

-- ------------------------------------------------------
-- 2. 吧务（吧主任命的管理员）
-- ------------------------------------------------------
-- 只记「谁是管理员」，吧主本人仍在 category.owner_id，
-- 判定某人能否管某个吧时 = owner_id 命中 OR bar_moderator 命中。
CREATE TABLE IF NOT EXISTS `bar_moderator` (
  `id` VARCHAR(36) NOT NULL COMMENT '主键',
  `bar_id` VARCHAR(36) NOT NULL COMMENT '书吧ID（category.id）',
  `user_id` VARCHAR(36) NOT NULL COMMENT '被任命的用户ID',
  `create_time` DATETIME DEFAULT NULL COMMENT '任命时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bar_user` (`bar_id`,`user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书吧管理员（吧主任命）';

-- ------------------------------------------------------
-- 3. 吧内成员：关注即入吧，等级与经验挂在这里
-- ------------------------------------------------------
-- 关注书吧 = 在 bar_member 里插一条记录；取关 = 删掉（等级经验一并作废）。
-- level 是 exp 的**快照**：列表/排序直接读列，不用每次现算；
-- 换算规则在后端 BarLevelService，改阈值时要重建一次快照。
-- daily_exp / daily_date 用于「每日经验上限」：跨天后 daily_date 变了就自动清零。
CREATE TABLE IF NOT EXISTS `bar_member` (
  `id` VARCHAR(36) NOT NULL COMMENT '主键',
  `bar_id` VARCHAR(36) NOT NULL COMMENT '书吧ID',
  `user_id` VARCHAR(36) NOT NULL COMMENT '用户ID',
  `exp` INT NOT NULL DEFAULT 0 COMMENT '吧内累计经验',
  `level` INT NOT NULL DEFAULT 1 COMMENT '吧内等级（exp 换算的快照）',
  `daily_exp` INT NOT NULL DEFAULT 0 COMMENT '当日已获得经验（每日上限用）',
  `daily_date` DATE DEFAULT NULL COMMENT 'daily_exp 归属日期，跨天自动重置',
  `create_time` DATETIME DEFAULT NULL COMMENT '入吧（关注）时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bar_user` (`bar_id`,`user_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_bar_exp` (`bar_id`,`exp`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书吧成员（关注 + 等级 + 经验）';

-- ------------------------------------------------------
-- 4. 等级称号（吧主自定义）
-- ------------------------------------------------------
-- 只有吧主设过的等级才有称号行；没有行的等级前端只显示「Lv.N」。
-- 这就是「默认无、但有等级字段」的含义：等级一定存在，称号可以为空。
CREATE TABLE IF NOT EXISTS `bar_level_title` (
  `id` VARCHAR(36) NOT NULL COMMENT '主键',
  `bar_id` VARCHAR(36) NOT NULL COMMENT '书吧ID',
  `level` INT NOT NULL COMMENT '等级（1~10）',
  `title` VARCHAR(20) NOT NULL COMMENT '称号名',
  `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
  `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_bar_level` (`bar_id`,`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书吧等级称号（吧主设定）';

-- ------------------------------------------------------
-- 5. 校验（纯 ASCII 输出，避免 PowerShell 控制台按 GBK 解码造成假乱码）
-- ------------------------------------------------------
SELECT 'poll_tables_left' AS k, COUNT(*) AS v
FROM information_schema.tables
WHERE table_schema = 'booknest' AND table_name IN ('post_poll','post_poll_vote');

SELECT 'new_tables' AS k, COUNT(*) AS v
FROM information_schema.tables
WHERE table_schema = 'booknest'
  AND table_name IN ('bar_moderator','bar_member','bar_level_title');
