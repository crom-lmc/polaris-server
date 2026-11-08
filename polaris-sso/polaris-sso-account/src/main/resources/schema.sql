-- ============================================================
-- SSO 微服务模板 - 账户模块表结构（MySQL 5.7 兼容）
-- 命名采用 account 语义（账户 = 可登录的凭据主体；用户 User 由 HR 系统管辖）
-- 主键统一使用雪花 ID（MyBatis-Plus ASSIGN_ID），故不自增
-- ============================================================

CREATE TABLE IF NOT EXISTS sso_account (
    id                BIGINT       NOT NULL COMMENT '雪花ID',
    username          VARCHAR(64)  NOT NULL COMMENT '登录名',
    nickname          VARCHAR(64)  DEFAULT '' COMMENT '昵称',
    email             VARCHAR(128) DEFAULT NULL COMMENT '邮箱（为空存 NULL，uk_email 允许多个 NULL）',
    phone             VARCHAR(32)  DEFAULT '' COMMENT '手机号',
    password_hash     VARCHAR(128) NOT NULL COMMENT 'BCrypt 密码哈希',
    avatar_url        VARCHAR(255) DEFAULT '' COMMENT '头像',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2禁用 3锁定 4未激活',
    source            VARCHAR(32)  DEFAULT 'local' COMMENT '来源：local/email/wx 等',
    pwd_reset_required TINYINT      NOT NULL DEFAULT 0 COMMENT '1=下次登录强制改密',
    login_fail_count  INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until      DATETIME     DEFAULT NULL COMMENT '锁定到期时间',
    last_login_at     DATETIME     DEFAULT NULL COMMENT '最近登录时间',
    user_id           BIGINT       DEFAULT NULL COMMENT '关联 HR 用户ID（人），账户归属人',
    created_at        DATETIME     DEFAULT NULL,
    updated_at        DATETIME     DEFAULT NULL,
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账户表';

CREATE TABLE IF NOT EXISTS sso_role (
    id          BIGINT      NOT NULL,
    code        VARCHAR(64) NOT NULL COMMENT '角色编码，如 SUPER_ADMIN',
    -- 角色归属的应用：sso 是平台自身，业务角色填 sso_application.code（如 ehr）。
    -- 角色只能授权同应用的权限点，见 RoleServiceImpl#validatePermissionScope。
    app_code    VARCHAR(32) NOT NULL DEFAULT 'sso' COMMENT '所属应用编码',
    name        VARCHAR(64) NOT NULL COMMENT '角色名称',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at  DATETIME    DEFAULT NULL,
    updated_at  DATETIME    DEFAULT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_code (code),
    KEY idx_role_app (app_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sso_permission (
    id          BIGINT      NOT NULL,
    app_code    VARCHAR(64) NOT NULL DEFAULT 'sso' COMMENT '所属应用编码；sso 表示平台自身，其余对应 sso_application.code',
    code        VARCHAR(128) NOT NULL COMMENT '权限码，如 account:create（按钮级）',
    name        VARCHAR(64) NOT NULL COMMENT '权限名称',
    type        TINYINT     NOT NULL DEFAULT 2 COMMENT '1=菜单 2=按钮',
    parent_id   BIGINT      DEFAULT 0 COMMENT '上级权限ID（菜单树，必须同应用）',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at  DATETIME    DEFAULT NULL,
    updated_at  DATETIME    DEFAULT NULL,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_perm_app_code (app_code, code),
    KEY idx_perm_app (app_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='权限表（菜单+按钮，按应用分域）';

CREATE TABLE IF NOT EXISTS sso_account_role (
    account_id  BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    PRIMARY KEY (account_id, role_id),
    KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账户-角色关联';

CREATE TABLE IF NOT EXISTS sso_role_permission (
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_perm (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='角色-权限关联';

-- 幂等修正历史库：早期版本 email 默认值是 ''，会让「无邮箱账户」在唯一索引上互相冲突。
-- CREATE TABLE IF NOT EXISTS 不会改动已存在的表结构，所以必须用 ALTER 兜一次。
ALTER TABLE sso_account MODIFY COLUMN email VARCHAR(128) DEFAULT NULL COMMENT '邮箱（为空存 NULL，uk_email 允许多个 NULL）';

-- ============================================================
-- 登录审计日志：谁、何时、从哪、用什么方式、成功还是失败。
-- 不放 deleted 逻辑删除列——审计记录必须全部保留，查询按时间倒序。
-- ============================================================
CREATE TABLE IF NOT EXISTS sso_login_log (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    account_id    BIGINT       DEFAULT NULL COMMENT '成功时填账户ID，用户不存在等失败场景为 NULL',
    username      VARCHAR(64)  NOT NULL COMMENT '尝试登录的用户名（即使不存在也记录，便于排查撞库）',
    login_type    VARCHAR(20)  NOT NULL DEFAULT 'password' COMMENT 'password/email/wechat',
    result        VARCHAR(10)  NOT NULL COMMENT 'success / fail',
    fail_reason   VARCHAR(100) DEFAULT NULL COMMENT '失败原因（密码错误/账户锁定/未配置等）',
    client_ip     VARCHAR(45)  DEFAULT NULL,
    user_agent    VARCHAR(512) DEFAULT NULL,
    created_at    DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_account_time (account_id, created_at),
    KEY idx_username_time (username, created_at),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='登录审计日志';

-- ============================================================
-- 微信绑定关系：一个账户可绑定多个开放平台/小程序 identity（app_type 区分）。
-- ============================================================
CREATE TABLE IF NOT EXISTS sso_wechat_bind (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    account_id  BIGINT       NOT NULL COMMENT '关联账户ID',
    openid      VARCHAR(64)  NOT NULL COMMENT '微信 openid（同一 app_type 唯一）',
    unionid     VARCHAR(64)  DEFAULT NULL COMMENT '开放平台 unionid（跨公众号/小程序同人）',
    app_type    VARCHAR(20)  NOT NULL DEFAULT 'open' COMMENT 'open=开放平台扫码 mp=小程序',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '微信昵称快照',
    avatar_url  VARCHAR(255) DEFAULT NULL COMMENT '微信头像快照',
    bind_at     DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openid_app (openid, app_type),
    KEY idx_account (account_id),
    KEY idx_unionid (unionid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='微信绑定关系';

-- ============================================================
-- 邮箱验证码：只存哈希（禁止明文），带过期与尝试次数；登录/绑定场景复用。
-- ============================================================
CREATE TABLE IF NOT EXISTS sso_email_code (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    email       VARCHAR(128) NOT NULL,
    code_hash   VARCHAR(64)  NOT NULL COMMENT '验证码 BCrypt 哈希，禁止明文',
    scene       VARCHAR(20)  NOT NULL DEFAULT 'login' COMMENT 'login=邮箱登录验证码',
    ip          VARCHAR(45)  DEFAULT NULL,
    expires_at  DATETIME     NOT NULL COMMENT '过期时间',
    used        TINYINT      NOT NULL DEFAULT 0 COMMENT '0=未使用 1=已使用',
    try_count   INT          NOT NULL DEFAULT 0 COMMENT '校验尝试次数',
    created_at  DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_email_scene (email, scene),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='邮箱验证码（含哈希与过期）';

-- ============================================================
-- 应用注册表：SSO 保护的下游系统清单。
-- 账号「能访问哪些应用」即应用级授权，与角色/权限码是两套维度：
--   权限码 = 进入 SSO 管理台后能点哪些按钮；
--   应用   = 这个账号的身份可以被哪些业务系统接受（ticket 换 token 时校验）。
-- app_secret 用于应用后端调用 /api/auth/ticket/exchange 时的身份凭据，
-- 防止任何人拿到 ticket 就能换 token。
-- ============================================================
CREATE TABLE IF NOT EXISTS sso_application (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    code        VARCHAR(64)  NOT NULL COMMENT '应用编码，如 blog-admin / fishing-admin',
    name        VARCHAR(64)  NOT NULL COMMENT '应用名称',
    base_url    VARCHAR(255) DEFAULT '' COMMENT '应用访问地址，如 https://blog.laimengchao.site',
    redirect_uri VARCHAR(512) DEFAULT '' COMMENT 'OAuth2 回调地址（授权码回跳点），与 base_url 同源',
    app_secret  VARCHAR(64)  NOT NULL COMMENT '应用密钥（32位随机串），exchange 时校验',
    icon        VARCHAR(64)  DEFAULT '' COMMENT '图标标识（前端选用）',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 2停用',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序',
    created_at  DATETIME     DEFAULT NULL,
    updated_at  DATETIME     DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='应用注册表';

-- ============================================================
-- 账号-应用授权：允许某账号登录某应用。超级管理员不受此表限制（视作全部授权）。
-- ============================================================
CREATE TABLE IF NOT EXISTS sso_account_app (
    account_id  BIGINT NOT NULL,
    app_id      BIGINT NOT NULL,
    PRIMARY KEY (account_id, app_id),
    KEY idx_app (app_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账号-应用授权';
