-- =========================================================
-- 2026-09-16 计数落库幂等账本：count_flush_ledger
--
-- 背景：浏览 / 点赞 / 评论 / 收藏这些计数改成「先加 Redis、CountFlushJob 每 30 秒批量落库」之后，
--       一次落库是「RENAME 换出 → 批量 UPDATE → DEL 暂存区」三步。若 UPDATE 已经提交、
--       而 DEL 失败（Redis 抖动、连接被掐、进程正好在这两步之间被 kill），
--       那批增量会留在暂存区里，被下一轮当「残留」再落一次 —— 同一批增量累加两次。
--
--       这个窗口没法靠「多试几次」或「调换顺序」消掉：把 DEL 提到 UPDATE 之前，
--       换来的是丢计数。两者之间不存在第三个原子点。
--       唯一的出路是让「落库」这件事本身幂等：每批增量带一个批次ID
--       （写在计数缓冲 HASH 的 __batch 字段里，由 RENAME 与增量原子地一起搬到暂存区），
--       落库时先按这个ID 在本表登记。登记与计数 UPDATE 放在同一个事务里，
--       于是「本表里有这个ID」严格等价于「这批已经写进 MySQL 了」：
--         重试命中已有ID → 只做确认删除，不再碰计数列。
--
-- 影响：纯新增表，不改任何既有表结构，对既有数据与既有逻辑零影响。
--       应用侧代码见 CountFlushLedgerMapper 与 CountFlushJob.writeToDb。
--       ⚠️ 表不存在时落库会抛异常 → 表现为「计数一直不落库、残留越堆越多」（计数本身不会算错），
--          所以「先执行本脚本，再部署新代码」。
--
-- 执行方式（本脚本不在 docker-entrypoint-initdb.d 扫描范围内 —— 数据目录已存在，
-- 官方镜像只在首次启动时才跑初始化脚本，所以这里必须手动执行）：
--   docker cp 2026-09-16-count-flush-ledger-upgrade.sql booknest-mysql:/tmp/u.sql
--   docker exec booknest-mysql sh -c \
--     "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD booknest < /tmp/u.sql"
--   跑完删掉容器内脚本：docker exec booknest-mysql rm -f /tmp/u.sql
--
-- 幂等：用 CREATE TABLE IF NOT EXISTS，重复执行不报错。
-- =========================================================
SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS booknest DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE booknest;

CREATE TABLE IF NOT EXISTS `count_flush_ledger` (
    -- 批次ID（UUID），取值与 Redis 计数缓冲 HASH 里的 __batch 字段一致。
    -- 直接用主键、不用自增列：应用侧生成的 UUID 就是身份本身，不需要数据库再发号，
    -- 也就不存在「先取号再落库」那次额外的往返与它带来的新窗口。
    `batch_id`   VARCHAR(36) NOT NULL COMMENT '计数落库批次ID（对应 Redis 缓冲 HASH 的 __batch 字段）',
    `applied_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '该批次写入 MySQL 的时间（仅用于按时间清理）',

    PRIMARY KEY (`batch_id`),
    -- 清理语句是「删掉 N 天前的记录」，没有索引就会全表扫
    KEY `idx_applied_at` (`applied_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='计数落库幂等账本：同一批次ID 只允许生效一次';

-- 建议就地验证一次（能报 1062 才说明主键约束真的生效）：
--   insert into booknest.count_flush_ledger(batch_id) values ('__verify__');   -- 期望成功
--   insert into booknest.count_flush_ledger(batch_id) values ('__verify__');   -- 期望 1062 Duplicate entry
--   delete from booknest.count_flush_ledger where batch_id = '__verify__';
