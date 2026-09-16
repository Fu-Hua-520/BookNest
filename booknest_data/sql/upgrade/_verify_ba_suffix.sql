-- ====================================
-- 升级后复核（纯 ASCII 输出，避免 PowerShell 控制台编码干扰）
-- ====================================
SET NAMES utf8mb4;
USE booknest;

SELECT 'total_bars' AS k, COUNT(*) AS v FROM `category`;
SELECT 'no_ba_suffix' AS k, COUNT(*) AS v FROM `category` WHERE RIGHT(`name`, 1) <> '吧';
SELECT 'with_parent' AS k, COUNT(*) AS v FROM `category` WHERE `parent_id` IS NOT NULL;
SELECT 'bad_name_bytes' AS k, COUNT(*) AS v
FROM `category` WHERE HEX(RIGHT(`name`, 1)) <> 'E590A7';
SELECT 'poll_tables' AS k, COUNT(*) AS v
FROM information_schema.tables
WHERE table_schema = 'booknest' AND table_name IN ('post_poll', 'post_poll_vote');
