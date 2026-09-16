-- =========================================================
-- 2026-09-14 通知消费幂等：NULL 安全去重键 uk_notification_dedup
--
-- 背景：MQ 只能保证 at-least-once，同一条通知消息被投递两次是常态而非异常
--       （消费成功但 ack 丢失、应用重启、失败重试 —— 本项目 yml 配了 3 次重试）。
--       消费端原先是一句裸 insert，重投一次就在通知列表里多一条一模一样的记录。
--       方案：加去重唯一键，消费端改用 INSERT IGNORE，重复消息影响行数 0，自然幂等。
--
-- =========================================================
-- A. 为什么不是「四列唯一键 (receiver_id, type, source_id, anchor_id)」
-- =========================================================
--   MySQL 唯一索引的判等是**逐列**的，而 NULL 之间**互不相等**（NULL != NULL）。
--   于是只要四列里有任何一列是 NULL，这一行就永远不可能与任何行「冲突」——
--   唯一键形同虚设。
--   本项目里 LIKE / COMMENT / FOLLOW 三类通知的 anchor_id 恒为 NULL
--   （只有 REPLY / AI_REPLY 会填锚点评论 ID），FOLLOW 连 source_id 都曾是 NULL。
--   实测（临时表，同一组值 INSERT IGNORE 两次）：
--       四列键 + (r1,'FOLLOW',NULL,NULL) × 2  →  实际落库 2 行  ❌ 没去重
--       生成列 + (r1,'FOLLOW',NULL,NULL) × 2  →  实际落库 1 行  ✅ 去重成功
--   所以正确做法是先把 NULL 折叠掉，再对折叠后的结果建唯一键。
--
-- =========================================================
-- B. 方案：STORED 生成列 dedup_key
-- =========================================================
--   dedup_key = CONCAT_WS(':', receiver_id, type, COALESCE(source_id,''), COALESCE(anchor_id,''))
--   生成列由 MySQL 自动维护，应用侧完全无感知 —— Notification 实体与
--   NotificationMapper.xml 的插入语句都不用改（它们列的是显式列名，
--   生成列不在其中，既不会被写入也不该被写入）。
--
--   分隔符用 ':' 是安全的：四个组成部分只可能是 UUID（十六进制 + 短横）
--   或全大写常量（LIKE/COMMENT/REPLY/FOLLOW/AI_REPLY/AUDIT），都不含 ':'。
--   既无歧义，又比 CHAR(1) 之类的不可见字符更好排查。
--
--   为什么不含 content / create_time：同一个人因为同一条评论/帖子收到的同类型通知，
--   业务上本来就只该有一条 —— 内容一致只是佐证，不是判重条件。带上时间反而会
--   因为两次投递时间不同而永远判不出重复，等于没加。
--
-- =========================================================
-- C. 为什么第 ① 步要先回填 FOLLOW 的 source_id（这一步会改数据，但不会丢数据）
-- =========================================================
--   FollowServiceImpl 原先发关注通知时 sourceId 传的是 null，导致：
--     · 产品缺陷：前端 NotificationView.onOpen 的 FOLLOW 分支是
--       `router.push('/user/' + item.sourceId)`，它前面还有 `if (!item.sourceId) return`，
--       传 null 会让「点关注通知」什么都不发生，那段跳转成了永远走不到的死代码。
--     · 建键障碍：多条 FOLLOW 通知的 source_id 全是 NULL，折叠后 dedup_key 完全相同。
--       若直接按此去重，会把「两个不同的人关注了你」这两条**各自独立**的通知
--       误判成同一条并删掉一条。
--   关注者的昵称就写在 content 里（`<昵称> 关注了你`），而 user.username 有唯一索引，
--   所以可以精确回填。本库实测：3 条 FOLLOW 通知全部与 user_follow 表一一吻合。
--   代码侧已同步修复（FollowServiceImpl 现在传关注者的 userId）——
--   注意：只有「先回填、后去重」，才不会丢合法数据。
--
-- ⚠️ 第 ② 步会 DELETE 数据。删掉的是「在新唯一键语义下本就无法共存」的行，
--    即与生效后的 invariant 完全一致。执行前请先备份（CREATE TABLE ... AS SELECT）。
--
-- 执行方式（本脚本不在 docker-entrypoint-initdb.d 扫描范围内，需手动跑）：
--   docker cp 2026-09-14-notification-dedup-upgrade.sql booknest-mysql:/tmp/u.sql
--   docker exec booknest-mysql sh -c \
--     "mysql --default-character-set=utf8mb4 -uroot -p\$MYSQL_ROOT_PASSWORD booknest < /tmp/u.sql"
--   跑完删掉容器内脚本，避免误留：docker exec booknest-mysql rm -f /tmp/u.sql
--
-- ✅ 本脚本可重复执行：②③④ 三步都先用 information_schema 判存在性，
--    已存在则打印提示并跳过，不会报 1060 (Duplicate column) / 1061 (Duplicate key name)。
--    ① 回填与 ② 去重在第二次执行时均为空操作。
-- =========================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------
-- 0. 回填前体检：先看清有哪些 FOLLOW 通知缺 source_id、能否匹配到用户
--    （匹配不上会留 NULL，需要人工介入；正常情况应全部匹配）
-- ---------------------------------------------------------
SELECT n.id, n.receiver_id AS followee_id, n.content,
       u.id AS backfill_to_user_id
FROM `notification` n
LEFT JOIN `user` u
       ON u.username = SUBSTRING_INDEX(n.content, ' 关注了你', 1)
WHERE n.type = 'FOLLOW'
  AND n.source_id IS NULL
  AND n.content LIKE '% 关注了你'
ORDER BY n.id;

-- ---------------------------------------------------------
-- 1. 回填 FOLLOW 通知的 source_id = 关注者用户ID
--    （username 是唯一索引，匹配是精确的；NULL 昵称不会参与 JOIN）
-- ---------------------------------------------------------
UPDATE `notification` n
    JOIN `user` u
      ON u.username = SUBSTRING_INDEX(n.content, ' 关注了你', 1)
SET n.source_id = u.id
WHERE n.type = 'FOLLOW'
  AND n.source_id IS NULL
  AND n.content LIKE '% 关注了你';

-- 回填后仍缺 source_id 的 FOLLOW 行数（应为 0；不为 0 说明昵称已被改过，需人工处理）
SELECT COUNT(*) AS follow_still_null_source FROM `notification`
WHERE `type` = 'FOLLOW' AND `source_id` IS NULL;

-- ---------------------------------------------------------
-- 2. 清理重复行（按新唯一键的语义分组；<=> 是 NULL 安全比较）
--    每个 (receiver_id, type, source_id, anchor_id) 只保留 id 最小的那条
-- ---------------------------------------------------------
DELETE n FROM `notification` n
    JOIN `notification` keep
        ON n.receiver_id <=> keep.receiver_id
        AND n.type <=> keep.type
        AND n.source_id <=> keep.source_id
        AND n.anchor_id <=> keep.anchor_id
        AND n.id > keep.id;

-- ---------------------------------------------------------
-- 3. 加生成列 dedup_key（把 NULL 折叠成空串，NULL 之间才有判等语义）
-- ---------------------------------------------------------
SET @has_col := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification'
      AND COLUMN_NAME = 'dedup_key'
);
SET @sql := IF(@has_col = 0,
    'ALTER TABLE `notification`
        ADD COLUMN `dedup_key` VARCHAR(255)
        AS (CONCAT_WS('':'' , receiver_id, `type`, COALESCE(source_id, ''''), COALESCE(anchor_id, ''''))) STORED
        COMMENT ''消费幂等去重键（生成列：NULL 折叠为空串，供唯一键使用）''',
    'SELECT ''dedup_key 已存在，跳过'' AS upgrade_step_3');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------
-- 4. 在生成列上建唯一键
-- ---------------------------------------------------------
SET @has_idx := (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notification'
      AND INDEX_NAME = 'uk_notification_dedup'
);
SET @sql := IF(@has_idx = 0,
    'ALTER TABLE `notification` ADD UNIQUE KEY `uk_notification_dedup` (`dedup_key`)',
    'SELECT ''uk_notification_dedup 已存在，跳过'' AS upgrade_step_4');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------
-- 5. 自检
-- ---------------------------------------------------------
-- 5.1 键是否就位
SELECT index_name, non_unique, seq_in_index, column_name
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification'
  AND INDEX_NAME = 'uk_notification_dedup';

-- 5.2 还剩几组重复（应为 0）
SELECT COUNT(*) AS remaining_dup_groups FROM (
    SELECT receiver_id, `type`, source_id, anchor_id
    FROM `notification`
    GROUP BY receiver_id, `type`, source_id, anchor_id
    HAVING COUNT(*) > 1
) t;

-- 5.3 生成列的实际取值（确认 NULL 已折叠、分隔符无歧义）
SELECT id, `type`, COALESCE(source_id,'(null)') AS src,
       COALESCE(anchor_id,'(null)') AS anc, dedup_key
FROM `notification` ORDER BY id;
