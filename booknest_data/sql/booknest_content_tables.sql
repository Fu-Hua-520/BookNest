-- ====================================
-- BookNest 书籍论坛核心内容域建表
-- 涵盖：书籍元数据、分类、标签、帖子、帖子标签关联、书单、书单条目
-- ====================================

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
    `id` VARCHAR(36) PRIMARY KEY COMMENT '分类ID（UUID）',
    `name` VARCHAR(50) NOT NULL COMMENT '分类名称',
    `parent_id` VARCHAR(36) DEFAULT NULL COMMENT '父分类ID，NULL表示一级分类',
    `sort_order` INT DEFAULT 0 COMMENT '排序序号，数字越小越靠前',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '分类描述',
    `status` INT DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    -- 索引
    INDEX `idx_parent_id` (`parent_id`),
    INDEX `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='书籍分类表（两级）';

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
    INDEX `idx_view_count` (`view_count`)
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
-- 初始化一级分类数据（8个）
-- ------------------------------------------------------
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '文学', NULL, 1, '文学作品与赏析', 1),
(UUID(), '小说', NULL, 2, '各类小说作品', 1),
(UUID(), '历史', NULL, 3, '历史读物与史学研究', 1),
(UUID(), '科幻', NULL, 4, '科幻与奇幻作品', 1),
(UUID(), '悬疑推理', NULL, 5, '悬疑、推理、犯罪题材', 1),
(UUID(), '经管', NULL, 6, '经济、管理与商业', 1),
(UUID(), '社科', NULL, 7, '社会科学与人文', 1),
(UUID(), '生活', NULL, 8, '生活百科与兴趣', 1);

-- ------------------------------------------------------
-- 初始化二级分类数据（每个一级下2~3个）
-- ------------------------------------------------------

-- 文学的子分类
SET @wenxue_id = (SELECT id FROM `category` WHERE name = '文学' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '散文', @wenxue_id, 1, '散文随笔', 1),
(UUID(), '诗歌', @wenxue_id, 2, '诗词歌赋', 1),
(UUID(), '文学评论', @wenxue_id, 3, '文学理论与评论', 1);

-- 小说的子分类
SET @xiaoshuo_id = (SELECT id FROM `category` WHERE name = '小说' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '现代小说', @xiaoshuo_id, 1, '现当代小说', 1),
(UUID(), '古典名著', @xiaoshuo_id, 2, '中外古典文学名著', 1),
(UUID(), '网络文学', @xiaoshuo_id, 3, '网络连载文学', 1);

-- 历史的子分类
SET @lishi_id = (SELECT id FROM `category` WHERE name = '历史' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '中国历史', @lishi_id, 1, '中国历史读物', 1),
(UUID(), '世界历史', @lishi_id, 2, '世界历史读物', 1),
(UUID(), '人物传记', @lishi_id, 3, '历史人物传记', 1);

-- 科幻的子分类
SET @kehuan_id = (SELECT id FROM `category` WHERE name = '科幻' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '硬科幻', @kehuan_id, 1, '硬核科幻作品', 1),
(UUID(), '软科幻', @kehuan_id, 2, '软科幻与人文科幻', 1),
(UUID(), '奇幻', @kehuan_id, 3, '奇幻魔幻作品', 1);

-- 悬疑推理的子分类
SET @xuanyi_id = (SELECT id FROM `category` WHERE name = '悬疑推理' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '推理小说', @xuanyi_id, 1, '本格/社会派推理', 1),
(UUID(), '惊悚悬疑', @xuanyi_id, 2, '惊悚悬疑作品', 1),
(UUID(), '犯罪纪实', @xuanyi_id, 3, '真实犯罪纪实文学', 1);

-- 经管的子分类
SET @jingguan_id = (SELECT id FROM `category` WHERE name = '经管' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '经济学', @jingguan_id, 1, '经济学理论与读物', 1),
(UUID(), '管理学', @jingguan_id, 2, '管理学与商业管理', 1),
(UUID(), '投资理财', @jingguan_id, 3, '投资与理财', 1);

-- 社科的子分类
SET @sheke_id = (SELECT id FROM `category` WHERE name = '社科' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '社会学', @sheke_id, 1, '社会学研究', 1),
(UUID(), '心理学', @sheke_id, 2, '心理学读物', 1),
(UUID(), '政治学', @sheke_id, 3, '政治与公共事务', 1);

-- 生活的子分类
SET @shenghuo_id = (SELECT id FROM `category` WHERE name = '生活' AND parent_id IS NULL LIMIT 1);
INSERT INTO `category` (`id`, `name`, `parent_id`, `sort_order`, `description`, `status`) VALUES
(UUID(), '美食', @shenghuo_id, 1, '美食与烹饪', 1),
(UUID(), '旅行', @shenghuo_id, 2, '旅行与地理', 1),
(UUID(), '健康养生', @shenghuo_id, 3, '健康与养生', 1);

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
