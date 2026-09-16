-- ====================================
-- 升级脚本：书吧化 + 求助贴 + 群邀请/加入申请
-- 目标库：已经初始化过、不想重建的 booknest 库
-- ====================================
--
-- 覆盖本次三项改造：
--   1. category 表支持「用户申请创建书吧 → 管理员审批」
--   2. post 表增加 post_type，用于「求助贴」这个独立帖子类型
--   3. 新建 chat_group_invitation，承载「邀请-同意」与「申请-批准」两个方向的进群请求
--
-- 使用方式（在宿主机执行，先 cd 到项目根 D:\FuHua520\booknest）：
--   docker cp booknest_data/sql/upgrade/2026-09-13-bar-helppost-upgrade.sql booknest-mysql:/tmp/u.sql
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
-- ⚠️ 一次性脚本，不可重复执行：MySQL 8.0 没有 ADD COLUMN IF NOT EXISTS，
--    重复执行会报 1060 Duplicate column name。若已执行过，忽略该报错即可。
--
-- ✅ 执行记录：2026-09-13 已在 booknest-mysql 上执行完成并通过校验——
--    category.owner_id / audit_status(默认1) / reject_reason 三列就位；
--    post.post_type(默认 NORMAL) 就位；
--    idx_owner_id / idx_audit_status / idx_post_type_publish_time 三个索引就位；
--    既有 32 条 category 的 audit_status 全部为 1（不会从站点消失）；
--    chat_group_invitation 表已建（InnoDB / utf8mb4_unicode_ci）。
--    故正常情况下**不需要再跑**，再跑只会得到 1060。
--
-- 为什么不把 ALTER 写进 booknest_content_tables.sql：
--    那些建表脚本挂在 /docker-entrypoint-initdb.d 下，只在数据目录为空（首次启动）时执行。
--    本文件放在 sql/upgrade/ 子目录，官方 entrypoint 不递归扫描，因此不会被自动执行。
--    （基础建表脚本也已同步补上这些列，保证全新部署与升级后的库结构一致。）

SET NAMES utf8mb4;

USE booknest;

-- ------------------------------------------------------
-- 1. category：书吧化，支持用户申请创建 + 管理员审批
-- ------------------------------------------------------
-- owner_id：吧主。历史分类与后台直接建的分类都留 NULL，视为「官方吧」。
-- audit_status：0-待审核 1-已通过 2-已驳回。既有数据全部回填为 1（已通过），
--               否则现有分类会集体从站点上消失。
-- 注意 AFTER 链：同一条 ALTER 里后一个 AFTER 可以引用前一个刚加的列，MySQL 8 支持。
ALTER TABLE `category`
    ADD COLUMN `owner_id` VARCHAR(36) DEFAULT NULL COMMENT '吧主用户ID（NULL 表示官方吧）' AFTER `status`,
    ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态：0-待审核 1-已通过 2-已驳回' AFTER `owner_id`,
    ADD COLUMN `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '驳回原因' AFTER `audit_status`;

ALTER TABLE `category`
    ADD INDEX `idx_owner_id` (`owner_id`),
    ADD INDEX `idx_audit_status` (`audit_status`);

-- ------------------------------------------------------
-- 2. post：帖子类型（NORMAL 普通书评 / HELP 求助贴）
-- ------------------------------------------------------
-- 默认 NORMAL 保证既有帖子语义不变；后端 PostTypeConstant 会做白名单归一化。
ALTER TABLE `post`
    ADD COLUMN `post_type` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '帖子类型：NORMAL-普通书评 HELP-求助贴' AFTER `category_id`;

-- 首页「书友求助」版块就是按 (post_type, publish_time) 取最新几条
ALTER TABLE `post`
    ADD INDEX `idx_post_type_publish_time` (`post_type`, `publish_time`);

-- ------------------------------------------------------
-- 3. 群邀请 / 加入申请表（一表两用）
-- ------------------------------------------------------
-- type 区分方向：INVITE 邀请他人（等被邀请人同意） / JOIN_REQUEST 主动申请（等群主批准）
-- invitee_id 在两种方向下都表示「要进群的那个人」，所以查「我收到的邀请」和
-- 查「我的申请进展」用的是同一个字段。
CREATE TABLE IF NOT EXISTS `chat_group_invitation` (
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

-- ------------------------------------------------------
-- 4. 校验
-- ------------------------------------------------------
-- category / post 的新列
SELECT table_name, column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'booknest'
  AND ( (table_name = 'category' AND column_name IN ('owner_id','audit_status','reject_reason'))
     OR (table_name = 'post'     AND column_name = 'post_type') )
ORDER BY table_name, ordinal_position;

-- 既有分类的审批状态必须全是 1（已通过），否则前台会看不到书吧
SELECT audit_status, count(*) AS cnt
FROM booknest.`category`
GROUP BY audit_status;

-- 新表是否建好
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'booknest' AND table_name = 'chat_group_invitation';
