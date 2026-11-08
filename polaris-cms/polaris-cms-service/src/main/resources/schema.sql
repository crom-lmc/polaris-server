-- ------------------------------------------------------------
-- Table DDL (idempotent CREATE TABLE IF NOT EXISTS, re-runnable on service start)
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `cms_article` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(128) NOT NULL COMMENT '标题',
  `summary` varchar(500) DEFAULT '' COMMENT '摘要（空则取正文前 200 字）',
  `content` longtext COMMENT '正文（Markdown/HTML 混存，前端编辑器决定）',
  `category_id` bigint(20) DEFAULT NULL COMMENT '分类',
  `tag_id` bigint(20) DEFAULT NULL COMMENT '主标签ID（单一标签，用于筛选）',
  `tag` varchar(255) DEFAULT '' COMMENT '标签名快照，多个用逗号分隔',
  `cover` varchar(255) DEFAULT '' COMMENT '封面图 URL',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0草稿 1待审核 2待发布(定时) 3已发布 4驳回 5已下线',
  `pinned` tinyint(4) NOT NULL DEFAULT '0' COMMENT '置顶',
  `recommended` tinyint(4) NOT NULL DEFAULT '0' COMMENT '推荐位',
  `seo_title` varchar(128) DEFAULT '' COMMENT 'SEO 标题（空则用 title）',
  `seo_keywords` varchar(255) DEFAULT '' COMMENT 'SEO 关键词',
  `seo_description` varchar(500) DEFAULT '' COMMENT 'SEO 描述（空则用 summary）',
  `publish_at` datetime DEFAULT NULL COMMENT '定时发布时间（status=2 时生效）',
  `published_at` datetime DEFAULT NULL COMMENT '实际发布时间',
  `author_id` bigint(20) NOT NULL COMMENT 'SSO 账户 ID',
  `author_name` varchar(64) NOT NULL COMMENT '作者名快照（头里只有 ASCII 登录名）',
  `submit_note` varchar(255) DEFAULT '' COMMENT '提审留言',
  `audit_note` varchar(255) DEFAULT '' COMMENT '审核意见（通过/驳回理由）',
  `auditor_name` varchar(64) DEFAULT NULL COMMENT '审核人',
  `audit_at` datetime DEFAULT NULL COMMENT '审核时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除：1=回收站',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `approval_instance_id` bigint(20) DEFAULT NULL COMMENT '内容发布审批实例ID（非空且 status=6 表示审批中）',
  PRIMARY KEY (`id`),
  KEY `idx_art_status` (`status`,`deleted`),
  KEY `idx_art_category` (`category_id`),
  KEY `idx_art_pubtime` (`published_at`),
  KEY `idx_art_tag` (`tag_id`),
  FULLTEXT KEY `ft_cms_search` (`title`,`content`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章';
CREATE TABLE IF NOT EXISTS `cms_article_tag` (
  `article_id` bigint(20) NOT NULL,
  `tag_id` bigint(20) NOT NULL,
  PRIMARY KEY (`article_id`,`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章-标签关联';
CREATE TABLE IF NOT EXISTS `cms_article_version` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `article_id` bigint(20) NOT NULL,
  `title` varchar(128) NOT NULL,
  `summary` varchar(500) DEFAULT '',
  `content` longtext,
  `remark` varchar(128) DEFAULT '' COMMENT '快照说明：发布/回滚自 vX',
  `operator` varchar(64) DEFAULT '' COMMENT '操作人',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ver_article` (`article_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章版本快照';
CREATE TABLE IF NOT EXISTS `cms_banner` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `title` varchar(128) NOT NULL COMMENT '标题（管理端识别用）',
  `image_url` varchar(255) NOT NULL COMMENT '图片 URL',
  `link_url` varchar(255) DEFAULT '' COMMENT '跳转地址',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序，小的在前',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `remark` varchar(255) DEFAULT '',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_banner_status` (`status`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Banner 管理';
CREATE TABLE IF NOT EXISTS `cms_category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '分类名',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序，小的在前',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除：1=回收站',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_cat_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类';
CREATE TABLE IF NOT EXISTS `cms_friend_link` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '站点名称',
  `url` varchar(255) NOT NULL DEFAULT '' COMMENT '跳转地址',
  `logo` varchar(255) DEFAULT '' COMMENT 'Logo 图片 URL（可空，空则显示名称首字）',
  `description` varchar(128) DEFAULT '' COMMENT '一句话描述',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序，小的在前',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_link_status` (`status`,`deleted`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='友情链接';
CREATE TABLE IF NOT EXISTS `cms_media` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `filename` varchar(128) NOT NULL COMMENT '存储文件名（重命名防穿越）',
  `original_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `url` varchar(255) NOT NULL COMMENT '访问 URL（/api/cms/media/file/...）',
  `content_type` varchar(128) DEFAULT '',
  `size` bigint(20) NOT NULL DEFAULT '0',
  `uploaded_by` bigint(20) NOT NULL COMMENT 'SSO 账户 ID',
  `uploaded_name` varchar(64) DEFAULT '',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_media_deleted` (`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体库';
CREATE TABLE IF NOT EXISTS `cms_portal_layout` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `module_key` varchar(64) NOT NULL COMMENT '模块标识（banner/featured/latest/category/tags/links）',
  `module_name` varchar(64) NOT NULL COMMENT '模块显示名',
  `enabled` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 0隐藏',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序，小的在前',
  `config` varchar(500) DEFAULT '' COMMENT '模块参数 JSON（如 {"limit":6}）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_module_key` (`module_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门户首页布局配置';
CREATE TABLE IF NOT EXISTS `cms_tag` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(64) NOT NULL COMMENT '标签名',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签';
