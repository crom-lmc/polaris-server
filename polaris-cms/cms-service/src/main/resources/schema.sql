-- ============================================================
-- CMS 库表结构（独立库 cms，MySQL 5.7+，全部幂等可重跑）
-- 每次发布/回滚都会写 cms_article_version 快照；回收站=逻辑删除 deleted=1
-- 全文检索用 ngram 分词的 FULLTEXT（中文按 2 字切分），查询失败自动回退 LIKE
-- ============================================================

CREATE TABLE IF NOT EXISTS cms_category (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64) NOT NULL COMMENT '分类名',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：1=回收站',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_cat_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类';

CREATE TABLE IF NOT EXISTS cms_tag (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(64) NOT NULL COMMENT '标签名',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签';

CREATE TABLE IF NOT EXISTS cms_article (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    title           VARCHAR(128) NOT NULL COMMENT '标题',
    summary         VARCHAR(500) DEFAULT '' COMMENT '摘要（空则取正文前 200 字）',
    content         LONGTEXT     COMMENT '正文（Markdown/HTML 混存，前端编辑器决定）',
    category_id     BIGINT       DEFAULT NULL COMMENT '分类',
    tag_id          BIGINT       DEFAULT NULL COMMENT '主标签ID（文案单一标签，用于筛选）',
    tag             VARCHAR(255) DEFAULT '' COMMENT '标签名快照，多个用逗号分隔（展示用，不参与筛选）',
    cover           VARCHAR(255) DEFAULT '' COMMENT '封面图 URL',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0草稿 1待审核 2待发布(定时) 3已发布 4驳回 5已下线',
    pinned          TINYINT      NOT NULL DEFAULT 0 COMMENT '置顶',
    recommended     TINYINT      NOT NULL DEFAULT 0 COMMENT '推荐位',
    seo_title       VARCHAR(128) DEFAULT '' COMMENT 'SEO 标题（空则用 title）',
    seo_keywords    VARCHAR(255) DEFAULT '' COMMENT 'SEO 关键词',
    seo_description VARCHAR(500) DEFAULT '' COMMENT 'SEO 描述（空则用 summary）',
    publish_at      DATETIME     DEFAULT NULL COMMENT '定时发布时间（status=2 时生效）',
    published_at    DATETIME     DEFAULT NULL COMMENT '实际发布时间',
    author_id       BIGINT       NOT NULL COMMENT 'SSO 账户 ID',
    author_name     VARCHAR(64)  NOT NULL COMMENT '作者名快照（头里只有 ASCII 登录名）',
    submit_note     VARCHAR(255) DEFAULT '' COMMENT '提审留言',
    audit_note      VARCHAR(255) DEFAULT '' COMMENT '审核意见（通过/驳回理由）',
    auditor_name    VARCHAR(64)  DEFAULT NULL COMMENT '审核人',
    audit_at        DATETIME     DEFAULT NULL COMMENT '审核时间',
    approval_instance_id BIGINT  DEFAULT NULL COMMENT '内容发布审批实例ID（status=6 时有效）',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：1=回收站',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_art_status (status, deleted),
    KEY idx_art_category (category_id),
    KEY idx_art_tag (tag_id),
    KEY idx_art_pubtime (published_at),
    FULLTEXT KEY ft_cms_search (title, content) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='CMS 文章';

CREATE TABLE IF NOT EXISTS cms_article_version (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    article_id  BIGINT      NOT NULL,
    title       VARCHAR(128) NOT NULL,
    summary     VARCHAR(500) DEFAULT '',
    content     LONGTEXT,
    remark      VARCHAR(128) DEFAULT '' COMMENT '快照说明：发布/回滚自 vX',
    operator    VARCHAR(64)  DEFAULT '' COMMENT '操作人',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ver_article (article_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章版本快照';

CREATE TABLE IF NOT EXISTS cms_media (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    filename     VARCHAR(128) NOT NULL COMMENT '存储文件名（重命名防穿越）',
    original_name VARCHAR(255) NOT NULL COMMENT '原始文件名',
    url          VARCHAR(255) NOT NULL COMMENT '访问 URL（/api/cms/media/file/...）',
    content_type VARCHAR(128) DEFAULT '',
    size         BIGINT       NOT NULL DEFAULT 0,
    uploaded_by  BIGINT       NOT NULL COMMENT 'SSO 账户 ID',
    uploaded_name VARCHAR(64) DEFAULT '',
    deleted      TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_media_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='媒体库';

CREATE TABLE IF NOT EXISTS cms_banner (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    title      VARCHAR(128) NOT NULL COMMENT '标题（管理端识别用）',
    image_url  VARCHAR(255) NOT NULL COMMENT '图片 URL',
    link_url   VARCHAR(255) DEFAULT '' COMMENT '跳转地址',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark     VARCHAR(255) DEFAULT '',
    deleted    TINYINT      NOT NULL DEFAULT 0,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_banner_status (status, deleted, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Banner 管理';

CREATE TABLE IF NOT EXISTS cms_friend_link (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT '站点名称',
    url         VARCHAR(255) NOT NULL DEFAULT '' COMMENT '跳转地址',
    logo        VARCHAR(255) DEFAULT '' COMMENT 'Logo 图片 URL（可空，空则显示名称首字）',
    description VARCHAR(128) DEFAULT '' COMMENT '一句话描述',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_link_status (status, deleted, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='友情链接';

CREATE TABLE IF NOT EXISTS cms_portal_layout (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    module_key  VARCHAR(64)  NOT NULL COMMENT '模块标识（banner/featured/latest/category/tags/links）',
    module_name VARCHAR(64)  NOT NULL COMMENT '模块显示名',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0隐藏',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序，小的在前',
    config      VARCHAR(500) DEFAULT '' COMMENT '模块参数 JSON（如 {"limit":6}）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_key (module_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门户首页布局配置';

