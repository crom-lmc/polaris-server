-- ------------------------------------------------------------
-- Table DDL (idempotent CREATE TABLE IF NOT EXISTS, re-runnable on service start)
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `t_article` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `title` varchar(200) NOT NULL COMMENT '标题',
  `slug` varchar(200) NOT NULL COMMENT 'URL 别名，唯一',
  `summary` varchar(500) DEFAULT NULL COMMENT '摘要',
  `cover` varchar(255) DEFAULT NULL COMMENT '封面图',
  `content_md` longtext COMMENT 'Markdown 原文',
  `content_html` longtext COMMENT '渲染后的 HTML（已 XSS 过滤）',
  `status` varchar(16) NOT NULL DEFAULT 'draft' COMMENT '状态：draft/published/private',
  `category_id` bigint(20) DEFAULT NULL COMMENT '分类 ID',
  `author_id` bigint(20) DEFAULT NULL COMMENT '浣滆?锛歋SO 璐︽埛 ID',
  `author_name` varchar(64) DEFAULT NULL COMMENT '浣滆?鐧诲綍鍚嶅揩鐓',
  `type` varchar(16) NOT NULL DEFAULT 'article' COMMENT '类型：article/page/note',
  `is_top` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否置顶',
  `allow_comment` tinyint(4) NOT NULL DEFAULT '1' COMMENT '是否允许评论',
  `view_count` int(11) NOT NULL DEFAULT '0' COMMENT '阅读数',
  `like_count` int(11) NOT NULL DEFAULT '0' COMMENT '点赞数',
  `comment_count` int(11) NOT NULL DEFAULT '0' COMMENT '评论数',
  `word_count` int(11) NOT NULL DEFAULT '0' COMMENT '字数',
  `reading_time` int(11) NOT NULL DEFAULT '0' COMMENT '预计阅读分钟数',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `search_text` mediumtext,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_slug` (`slug`),
  KEY `idx_article_status_time` (`status`,`published_at`),
  KEY `idx_article_category` (`category_id`),
  KEY `idx_article_type` (`type`),
  KEY `idx_article_views` (`view_count`),
  KEY `idx_article_author` (`author_id`),
  FULLTEXT KEY `ft_article_search` (`title`,`summary`,`search_text`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章';
CREATE TABLE IF NOT EXISTS `t_article_tag` (
  `article_id` bigint(20) NOT NULL COMMENT '文章 ID',
  `tag_id` bigint(20) NOT NULL COMMENT '标签 ID',
  PRIMARY KEY (`article_id`,`tag_id`),
  KEY `idx_article_tag_tag` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联';
CREATE TABLE IF NOT EXISTS `t_category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '名称',
  `slug` varchar(100) NOT NULL COMMENT '别名',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父级 ID，0 为顶级',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序，越大越靠前',
  `article_count` int(11) NOT NULL DEFAULT '0' COMMENT '文章数（冗余）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_slug` (`slug`),
  KEY `idx_category_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类';
CREATE TABLE IF NOT EXISTS `t_comment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `article_id` bigint(20) NOT NULL COMMENT '文章 ID',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父评论 ID，0 为顶层',
  `author_name` varchar(64) NOT NULL COMMENT '评论者昵称',
  `author_email` varchar(128) DEFAULT NULL COMMENT '评论者邮箱',
  `author_site` varchar(255) DEFAULT NULL COMMENT '评论者站点',
  `author_avatar` varchar(255) DEFAULT NULL COMMENT '评论者头像',
  `content` text NOT NULL COMMENT '评论内容',
  `status` varchar(16) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/spam/deleted',
  `user_agent` varchar(500) DEFAULT NULL COMMENT 'UA',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP',
  `is_admin` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否管理员评论',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_comment_article_status` (`article_id`,`status`),
  KEY `idx_comment_created` (`created_at`),
  KEY `idx_comment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';
CREATE TABLE IF NOT EXISTS `t_media` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `file_name` varchar(255) NOT NULL COMMENT '存储文件名',
  `original_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `url` varchar(500) NOT NULL COMMENT '访问 URL',
  `mime_type` varchar(100) DEFAULT NULL COMMENT 'MIME',
  `size` bigint(20) NOT NULL DEFAULT '0' COMMENT '字节大小',
  `width` int(11) DEFAULT NULL COMMENT '图片宽度',
  `height` int(11) DEFAULT NULL COMMENT '图片高度',
  `folder` varchar(100) NOT NULL DEFAULT '' COMMENT '目录',
  `folder_id` bigint(20) DEFAULT NULL COMMENT '逻辑目录 ID',
  `uploader_id` bigint(20) DEFAULT NULL COMMENT '上传者',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_media_folder` (`folder`),
  KEY `idx_media_created` (`created_at`),
  KEY `idx_media_folder_id` (`folder_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体库';
CREATE TABLE IF NOT EXISTS `t_media_folder` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '目录名称',
  `parent_id` bigint(20) NOT NULL DEFAULT '0' COMMENT '父目录 ID,0 为顶级',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_media_folder_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体目录';
CREATE TABLE IF NOT EXISTS `t_operation_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint(20) DEFAULT NULL COMMENT '操作人',
  `module` varchar(64) DEFAULT NULL COMMENT '模块',
  `action` varchar(64) DEFAULT NULL COMMENT '动作',
  `detail` varchar(1000) DEFAULT NULL COMMENT '详情',
  `ip` varchar(64) DEFAULT NULL COMMENT 'IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_log_created` (`created_at`),
  KEY `idx_log_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志';
CREATE TABLE IF NOT EXISTS `t_setting` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `setting_key` varchar(100) NOT NULL COMMENT '键',
  `setting_value` longtext COMMENT '值（JSON 字符串或文本）',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站点设置';
CREATE TABLE IF NOT EXISTS `t_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '名称',
  `slug` varchar(100) NOT NULL COMMENT '别名',
  `color` varchar(16) DEFAULT NULL COMMENT '标签云颜色',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_slug` (`slug`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签';
CREATE TABLE IF NOT EXISTS `t_theme` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) NOT NULL COMMENT '主题名称',
  `description` varchar(500) DEFAULT NULL COMMENT '描述',
  `config_json` longtext COMMENT '主题配置 JSON 字符串（不用 MySQL JSON 类型，保证 5.7 兼容）',
  `is_active` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否启用',
  `version` int(11) NOT NULL DEFAULT '1' COMMENT '版本号，每次发布 +1，用于前台缓存失效',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_builtin` tinyint(4) NOT NULL DEFAULT '0' COMMENT '是否内置/默认主题：1=内置（不可删除）',
  PRIMARY KEY (`id`),
  KEY `idx_theme_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主题';
CREATE TABLE IF NOT EXISTS `t_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(64) NOT NULL COMMENT '登录名',
  `nickname` varchar(64) DEFAULT NULL COMMENT '昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱',
  `password_hash` varchar(128) NOT NULL COMMENT 'BCrypt 密码摘要',
  `role` varchar(16) NOT NULL DEFAULT 'author' COMMENT '角色：admin/author',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '状态：1启用 0禁用',
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';
