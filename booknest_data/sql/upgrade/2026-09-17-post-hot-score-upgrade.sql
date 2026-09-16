-- ============================================================================
-- 让 post 的「热度」排序走索引
--
-- 背景：热榜的排序是表达式 (view_count + like_count * 5 + comment_count * 3)，
-- 表达式的值没有索引可用，MySQL 只能把候选行全部取出来做 filesort。
-- 改前的实测 EXPLAIN（booknest 库）：公开列表 + hot 排序 → Using filesort。
--
-- 做法：用 STORED 生成列把表达式物化成一列，再建一条能覆盖整个 ORDER BY 的索引。
--   · 生成列由 MySQL 自动维护（三个计数列一变它就重算），业务代码不必写它，
--     所以 CountFlushJob 的批量 UPDATE 与其它 update 都不受影响；
--   · 索引把 status / audit_status 这两个「等值过滤列」放在最前 ——
--     公开列表恒带 status = 1 AND audit_status = 1，等值列在前，
--     后面的 hot_score / publish_time 才能按序取用，filesort 才会真正消失；
--     若把 hot_score 放最前，等值列在后，MySQL 就用不上这个顺序了。
--
-- ⚠️ 表达式的唯一归属地：改完本脚本后，热度表达式只存在于这个生成列里，
--    PostMapper.xml 的 hot 分支已改为 order by hot_score desc, publish_time desc。
--    若将来要调整热度权重，只需在建/改生成列这一处动手，XML 不必再动。
--
-- ⚠️ 生成列不能带 DEFAULT：MySQL 8 的语法是
--    ADD COLUMN name type [GENERATED ALWAYS] AS (expr) STORED [NOT NULL] [COMMENT '..']
--    DEFAULT 必须省略（写了直接 1064，整条 ALTER 回滚）。
--    这里刻意不加 NOT NULL：若三个计数列存在 NULL，加 NOT NULL 会让 ALTER 直接失败。
--
-- 可重入：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，故用 information_schema 判存在性；
-- 重复执行只会打印一行 info，不报错。
-- ============================================================================

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'booknest' AND TABLE_NAME = 'post' AND COLUMN_NAME = 'hot_score'
);
SET @sql := IF(@col_exists = 0,
    'ALTER TABLE booknest.`post`
        ADD COLUMN `hot_score` INT
        GENERATED ALWAYS AS (view_count + like_count * 5 + comment_count * 3) STORED
        COMMENT ''热榜排序分 = view_count + like_count*5 + comment_count*3（生成列自动维护）''',
    'SELECT ''hot_score 已存在，跳过 ADD COLUMN'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = 'booknest' AND TABLE_NAME = 'post' AND INDEX_NAME = 'idx_hot_rank'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX `idx_hot_rank` ON booknest.`post`
        (`status`, `audit_status`, `hot_score` DESC, `publish_time` DESC)',
    'SELECT ''idx_hot_rank 已存在，跳过 CREATE INDEX'' AS info');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 校验：生成列是否与表达式一致、索引是否建成
SELECT 'VERIFY_COLUMN' AS sec, COLUMN_NAME, GENERATION_EXPRESSION, EXTRA
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'booknest' AND TABLE_NAME = 'post' AND COLUMN_NAME = 'hot_score';

SELECT 'VERIFY_INDEX' AS sec, INDEX_NAME, SEQ_IN_INDEX, COLUMN_NAME, COLLATION
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'booknest' AND TABLE_NAME = 'post' AND INDEX_NAME = 'idx_hot_rank'
ORDER BY SEQ_IN_INDEX;
