-- ====================================
-- BookNest 书籍论坛核心内容域建表
-- 涵盖：书籍元数据、分类、标签、帖子、帖子标签关联、书单、书单条目
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
-- 1. 书籍元数据表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `book`;
CREATE TABLE `book` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '书籍ID（UUID）',
    `title` VARCHAR(255) NOT NULL COMMENT '书名',
    `author` VARCHAR(255) DEFAULT NULL COMMENT '作者',
    `isbn` VARCHAR(20) DEFAULT NULL COMMENT 'ISBN号（唯一）',
    `cover_url` VARCHAR(500) DEFAULT NULL COMMENT '封面图片URL',
    `description` TEXT COMMENT '书籍简介',
    `publisher` VARCHAR(255) DEFAULT NULL COMMENT '出版社',
    `publish_date` VARCHAR(50) DEFAULT NULL COMMENT '出版日期（日期或年份字符串）',
    `rating` DECIMAL(3,1) DEFAULT 0.0 COMMENT '豆瓣式评分（0.0-10.0，保留1位小数）',
    `rating_count` INT DEFAULT 0 COMMENT '评分人数',
    `source` VARCHAR(20) DEFAULT NULL COMMENT '数据来源：manual/google/openlibrary/douban',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX `idx_title` (`title`),
    UNIQUE KEY `uk_isbn` (`isbn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍元数据表';

-- ------------------------------------------------------
-- 2. 两级分类表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '书吧ID（UUID）',
    `name` VARCHAR(50) NOT NULL COMMENT '书吧名称，例如「科幻小说吧」',
    `icon` VARCHAR(500) DEFAULT NULL COMMENT '书吧图标URL（为空时前端用吧名首字兜底）',
    `parent_id` VARCHAR(36) DEFAULT NULL COMMENT '【遗留列】书吧已取消分级，恒为 NULL，仅为兼容旧数据保留',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号，数字越小越靠前',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '书吧简介',
    `status` INT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `owner_id` VARCHAR(36) DEFAULT NULL COMMENT '吧主用户ID（NULL 表示官方吧）',
    `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态：0-待审核 1-已通过 2-已驳回',
    `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '驳回原因',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_sort_order` (`sort_order`),
    INDEX `idx_owner_id` (`owner_id`),
    INDEX `idx_audit_status` (`audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书吧表（原书籍分类表；已取消分级，所有吧平级）';

-- ------------------------------------------------------
-- 3. 标签表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '标签ID（UUID）',
    `name` VARCHAR(30) NOT NULL COMMENT '标签名称（唯一）',
    `use_count` INT DEFAULT 0 COMMENT '使用次数（冗余字段，用于热门标签排序）',
    `status` INT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    UNIQUE KEY `uk_name` (`name`),
    INDEX `idx_use_count` (`use_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍标签表';

-- ------------------------------------------------------
-- 4. 帖子/书评表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `post`;
CREATE TABLE `post` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '帖子ID（UUID）',
    `user_id` VARCHAR(36) NOT NULL COMMENT '作者用户ID',
    `book_id` VARCHAR(36) DEFAULT NULL COMMENT '关联书籍ID（可空）',
    `title` VARCHAR(255) NOT NULL COMMENT '帖子标题',
    `summary` TEXT COMMENT '帖子摘要',
    `content_url` VARCHAR(500) NOT NULL COMMENT '正文Markdown的OSS地址',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `category_id` VARCHAR(36) DEFAULT NULL COMMENT '分类ID',
    `post_type` VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '帖子类型：NORMAL-普通书评 HELP-求助贴',
    `view_count` BIGINT DEFAULT 0 COMMENT '浏览量',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT DEFAULT 0 COMMENT '评论数',
    `collect_count` INT DEFAULT 0 COMMENT '收藏数',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-草稿 1-已发布 2-已下架',
    `audit_status` TINYINT DEFAULT 0 COMMENT '审核状态：0-待审核 1-通过 2-拒绝',
    `audit_reason` VARCHAR(500) DEFAULT NULL COMMENT '审核拒绝原因',
    `audit_time` DATETIME DEFAULT NULL COMMENT '审核时间',
    `is_top` TINYINT DEFAULT 0 COMMENT '是否置顶：0-否 1-是',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `publish_time` DATETIME DEFAULT NULL COMMENT '发布时间',

    -- 索引
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status_publish_time` (`status`, `publish_time`),
    INDEX `idx_category_publish_time` (`category_id`, `publish_time`),
    INDEX `idx_publish_time` (`publish_time`),
    INDEX `idx_view_count` (`view_count`),
    INDEX `idx_post_type_publish_time` (`post_type`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子/书评表';

-- ------------------------------------------------------
-- 5. 帖子-标签关联表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `post_tag`;
CREATE TABLE `post_tag` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '关联ID（UUID）',
    `post_id` VARCHAR(36) NOT NULL COMMENT '帖子ID',
    `tag_id` VARCHAR(36) NOT NULL COMMENT '标签ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    UNIQUE KEY `uk_post_tag` (`post_id`, `tag_id`),
    INDEX `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子-标签关联表';

-- ------------------------------------------------------
-- 6. 书单表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `booklist`;
CREATE TABLE `booklist` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '书单ID（UUID）',
    `user_id` VARCHAR(36) NOT NULL COMMENT '创建者用户ID',
    `title` VARCHAR(255) NOT NULL COMMENT '书单标题',
    `summary` TEXT COMMENT '书单简介',
    `cover_image` VARCHAR(500) DEFAULT NULL COMMENT '书单封面图URL',
    `visibility` TINYINT DEFAULT 0 COMMENT '可见性：0-公开 1-私密',
    `like_count` INT DEFAULT 0 COMMENT '点赞数',
    `collect_count` INT DEFAULT 0 COMMENT '收藏数',
    `book_count` INT DEFAULT 0 COMMENT '书籍数量（冗余字段）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_visibility` (`visibility`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书单表';

-- ------------------------------------------------------
-- 7. 书单条目表
-- ------------------------------------------------------
DROP TABLE IF EXISTS `booklist_item`;
CREATE TABLE `booklist_item` (
    `id` VARCHAR(36) PRIMARY KEY COMMENT '条目ID（UUID）',
    `booklist_id` VARCHAR(36) NOT NULL COMMENT '所属书单ID',
    `book_id` VARCHAR(36) NOT NULL COMMENT '书籍ID',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号，数字越小越靠前',
    `note` VARCHAR(500) DEFAULT NULL COMMENT '推荐理由/备注',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    -- 索引
    INDEX `idx_booklist_id` (`booklist_id`),
    INDEX `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书单条目表';

-- ====================================
-- 初始数据
-- ====================================

-- ------------------------------------------------------
-- 初始化书吧数据（32 个吧，全部平级、无父级）
-- ------------------------------------------------------
-- 书吧已取消「分类树」结构：下面这些吧彼此独立，谁也不是谁的子级。
-- 命名统一带「吧」后缀（贴吧式叫法），前端直接展示 name 即可。
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '文学吧',   NULL, 1,  '文学作品与赏析', 1),
(UUID(), '散文吧',   NULL, 2,  '散文随笔', 1),
(UUID(), '诗歌吧',   NULL, 3,  '诗词歌赋', 1),
(UUID(), '文学评论吧', NULL, 4, '文学理论与评论', 1),
(UUID(), '小说吧',   NULL, 5,  '各类小说作品', 1),
(UUID(), '现代小说吧', NULL, 6, '现当代小说', 1),
(UUID(), '古典名著吧', NULL, 7, '中外古典文学名著', 1),
(UUID(), '网络文学吧', NULL, 8, '网络连载文学', 1),
(UUID(), '历史吧',   NULL, 9,  '历史读物与史学研究', 1),
(UUID(), '中国历史吧', NULL, 10, '中国历史读物', 1),
(UUID(), '世界历史吧', NULL, 11, '世界历史读物', 1),
(UUID(), '人物传记吧', NULL, 12, '历史人物传记', 1),
(UUID(), '科幻吧',   NULL, 13, '科幻与奇幻作品', 1),
(UUID(), '硬科幻吧', NULL, 14, '硬核科幻作品', 1),
(UUID(), '软科幻吧', NULL, 15, '软科幻与人文科幻', 1),
(UUID(), '奇幻吧',   NULL, 16, '奇幻魔幻作品', 1),
(UUID(), '悬疑推理吧', NULL, 17, '悬疑、推理、犯罪题材', 1),
(UUID(), '推理小说吧', NULL, 18, '本格/社会派推理', 1),
(UUID(), '惊悚悬疑吧', NULL, 19, '惊悚悬疑作品', 1),
(UUID(), '犯罪纪实吧', NULL, 20, '真实犯罪纪实文学', 1),
(UUID(), '经管吧',   NULL, 21, '经济、管理与商业', 1),
(UUID(), '经济学吧', NULL, 22, '经济学理论与读物', 1),
(UUID(), '管理学吧', NULL, 23, '管理学与商业管理', 1),
(UUID(), '投资理财吧', NULL, 24, '投资与理财', 1),
(UUID(), '社科吧',   NULL, 25, '社会科学与人文', 1),
(UUID(), '社会学吧', NULL, 26, '社会学研究', 1),
(UUID(), '心理学吧', NULL, 27, '心理学读物', 1),
(UUID(), '政治学吧', NULL, 28, '政治与公共事务', 1),
(UUID(), '生活吧',   NULL, 29, '生活百科与兴趣', 1),
(UUID(), '美食吧',   NULL, 30, '美食与烹饪', 1),
(UUID(), '旅行吧',   NULL, 31, '旅行与地理', 1),
(UUID(), '健康养生吧', NULL, 32, '健康与养生', 1);

-- ------------------------------------------------------
-- 初始化标签数据（22个）
-- ------------------------------------------------------
INSERT INTO `tag` (`id`, `name`, `use_count`, `status`) VALUES
(UUID(), '经典', 0, 1),
(UUID(), '文学', 0, 1),
(UUID(), '小说', 0, 1),
(UUID(), '科幻', 0, 1),
(UUID(), '悬疑', 0, 1),
(UUID(), '推理', 0, 1),
(UUID(), '心理', 0, 1),
(UUID(), '成长', 0, 1),
(UUID(), '历史', 0, 1),
(UUID(), '传记', 0, 1),
(UUID(), '散文', 0, 1),
(UUID(), '诗集', 0, 1),
(UUID(), '哲学', 0, 1),
(UUID(), '爱情', 0, 1),
(UUID(), '职场', 0, 1),
(UUID(), '励志', 0, 1),
(UUID(), '治愈', 0, 1),
(UUID(), '科普', 0, 1),
(UUID(), '冒险', 0, 1),
(UUID(), '战争', 0, 1),
(UUID(), '社会', 0, 1),
(UUID(), '艺术', 0, 1);
