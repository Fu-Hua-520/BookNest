-- =========================================================
-- 2026-09-14 通知锚点列（anchor_id）
--
-- 背景：新增通知类型 REPLY（别人回复了你的评论）。点这种通知要直接滚到
--       那条评论并高亮，但 notification.source_id 已经用来存帖子 ID（决定跳哪个
--       页面），一个字段塞不下两个 uuid，所以另开一列存评论 ID。
--
-- 影响：notification 表新增可空列，其余类型（LIKE/COMMENT/FOLLOW）留 NULL，
--       对既有数据与既有逻辑零影响。
--       顺带：type 列是 VARCHAR(20)，'REPLY' 直接可用，无需改动。
--
-- 执行方式（本脚本不在 docker-entrypoint-initdb.d 扫描范围内，需手动跑）：
--   docker cp 2026-09-14-notification-anchor-upgrade.sql booknest-mysql:/tmp/u.sql
--   docker exec booknest-mysql sh -c \
--     "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD booknest < /tmp/u.sql"
--   跑完删掉容器内脚本，避免重复执行：docker exec booknest-mysql rm -f /tmp/u.sql
--
-- ⚠️ 重复执行会报 1060 Duplicate column name: anchor_id，属预期（列已存在）。
-- =========================================================
SET NAMES utf8mb4;

ALTER TABLE `notification`
    ADD COLUMN `anchor_id` VARCHAR(36) DEFAULT NULL COMMENT '锚点ID：REPLY 存评论ID，点击通知定位到该评论' AFTER `source_id`;
