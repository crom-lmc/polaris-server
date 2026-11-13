-- ------------------------------------------------------------
-- Table DDL (idempotent CREATE TABLE IF NOT EXISTS, re-runnable on service start)
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `sso_account` (
  `id` bigint(20) NOT NULL COMMENT '闆姳ID',
  `username` varchar(64) NOT NULL COMMENT '鐧诲綍鍚�',
  `nickname` varchar(64) DEFAULT '' COMMENT '昵称\r',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱（为空存 NULL，uk_email 允许多个 NULL）',
  `phone` varchar(32) DEFAULT '' COMMENT '鎵嬫満鍙�',
  `password_hash` varchar(128) NOT NULL COMMENT 'BCrypt 密码哈希\r',
  `avatar_url` varchar(255) DEFAULT '' COMMENT '头像\r',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1姝ｅ父 2绂佺敤 3閿佸畾 4鏈縺娲�',
  `source` varchar(32) DEFAULT 'local' COMMENT '鏉ユ簮锛歭ocal/email/wx 绛�',
  `pwd_reset_required` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1=涓嬫鐧诲綍寮哄埗鏀瑰瘑',
  `login_fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '连续登录失败次数\r',
  `locked_until` datetime DEFAULT NULL COMMENT '锁定到期时间\r',
  `last_login_at` datetime DEFAULT NULL COMMENT '鏈�杩戠櫥褰曟椂闂�',
  `user_id` bigint(20) DEFAULT NULL COMMENT '鍏宠仈 HR 鐢ㄦ埛ID锛堜汉锛夛紝璐︽埛褰掑睘浜�',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除\r',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账号表';
CREATE TABLE IF NOT EXISTS `sso_account_app` (
  `account_id` bigint(20) NOT NULL,
  `app_id` bigint(20) NOT NULL,
  PRIMARY KEY (`account_id`,`app_id`),
  KEY `idx_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账号-应用授权';
CREATE TABLE IF NOT EXISTS `sso_account_role` (
  `account_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`account_id`,`role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账号-角色关联';
CREATE TABLE IF NOT EXISTS `sso_application` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `code` varchar(64) NOT NULL COMMENT '应用编码，如 blog-admin / fishing-admin',
  `name` varchar(64) NOT NULL COMMENT '应用名称',
  `base_url` varchar(255) DEFAULT '' COMMENT '应用访问地址，如 https://blog.laimengchao.site',
  `app_secret` varchar(64) NOT NULL COMMENT '应用密钥（32位随机串），exchange 时校验',
  `icon` varchar(64) DEFAULT '' COMMENT '图标标识（前端选用）',
  `remark` varchar(255) DEFAULT '' COMMENT '备注',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 2停用',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `redirect_uri` varchar(512) NOT NULL DEFAULT '' COMMENT 'OAuth2 回调地址\r',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='应用注册表';
CREATE TABLE IF NOT EXISTS `sso_email_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `email` varchar(128) NOT NULL,
  `code_hash` varchar(64) NOT NULL COMMENT '验证码 BCrypt 哈希，禁止明文',
  `scene` varchar(20) NOT NULL DEFAULT 'login' COMMENT 'login=邮箱登录验证码',
  `ip` varchar(45) DEFAULT NULL,
  `expires_at` datetime NOT NULL COMMENT '过期时间',
  `used` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0=未使用 1=已使用',
  `try_count` int(11) NOT NULL DEFAULT '0' COMMENT '校验尝试次数',
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_email_scene` (`email`,`scene`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='邮箱验证码（含哈希与过期）';
CREATE TABLE IF NOT EXISTS `sso_login_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_id` bigint(20) DEFAULT NULL COMMENT '成功时填账户ID，用户不存在等失败场景为 NULL',
  `username` varchar(64) NOT NULL COMMENT '尝试登录的用户名（即使不存在也记录，便于排查撞库）',
  `login_type` varchar(20) NOT NULL DEFAULT 'password' COMMENT 'password/email/wechat',
  `result` varchar(10) NOT NULL COMMENT 'success / fail',
  `fail_reason` varchar(100) DEFAULT NULL COMMENT '失败原因（密码错误/账户锁定/未配置等）',
  `client_ip` varchar(45) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_account_time` (`account_id`,`created_at`),
  KEY `idx_username_time` (`username`,`created_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='登录审计日志';
CREATE TABLE IF NOT EXISTS `sso_audit_log` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `operator_id` bigint(20) NOT NULL COMMENT '操作人账号ID',
  `operator_name` varchar(64) NOT NULL DEFAULT '' COMMENT '操作人登录名',
  `action` varchar(32) NOT NULL COMMENT '操作类型',
  `target_type` varchar(32) NOT NULL COMMENT '操作对象类型',
  `target_id` bigint(20) DEFAULT NULL COMMENT '操作对象ID',
  `fields` varchar(255) NOT NULL DEFAULT '' COMMENT 'result=success/fail及非敏感详情',
  `ip` varchar(64) NOT NULL DEFAULT '' COMMENT '客户端IP',
  `ua` varchar(255) NOT NULL DEFAULT '' COMMENT 'User-Agent',
  `created_at` datetime NOT NULL COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_operator_time` (`operator_id`,`created_at`),
  KEY `idx_audit_target` (`target_type`,`target_id`,`created_at`),
  KEY `idx_audit_action_time` (`action`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='SSO敏感操作审计日志';
CREATE TABLE IF NOT EXISTS `sso_permission` (
  `id` bigint(20) NOT NULL,
  `code` varchar(128) NOT NULL COMMENT '鏉冮檺鐮侊紝濡� account:create锛堟寜閽骇锛�',
  `name` varchar(64) NOT NULL COMMENT '权限名称\r',
  `type` tinyint(4) NOT NULL DEFAULT '2' COMMENT '1=菜单 2=按钮\r',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '涓婄骇鏉冮檺ID锛堣彍鍗曟爲锛�',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序\r',
  `remark` varchar(255) DEFAULT '' COMMENT '备注\r',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `app_code` varchar(64) NOT NULL DEFAULT 'sso' COMMENT '所属应用编码；sso 表示平台自身',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_app_code` (`app_code`,`code`),
  KEY `idx_perm_app` (`app_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='权限表（菜单+按钮）';
CREATE TABLE IF NOT EXISTS `sso_role` (
  `id` bigint(20) NOT NULL,
  `code` varchar(64) NOT NULL COMMENT '瑙掕壊缂栫爜锛屽 SUPER_ADMIN',
  `app_code` varchar(32) NOT NULL DEFAULT 'sso' COMMENT '所属应用编码',
  `name` varchar(64) NOT NULL COMMENT '角色名称\r',
  `remark` varchar(255) DEFAULT '' COMMENT '备注\r',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`),
  KEY `idx_role_app` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='角色表';
CREATE TABLE IF NOT EXISTS `sso_role_permission` (
  `role_id` bigint(20) NOT NULL,
  `permission_id` bigint(20) NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `idx_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='角色-权限关联';
CREATE TABLE IF NOT EXISTS `sso_wechat_bind` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_id` bigint(20) NOT NULL COMMENT '关联账户ID',
  `openid` varchar(64) NOT NULL COMMENT '微信 openid（同一 app_type 唯一）',
  `unionid` varchar(64) DEFAULT NULL COMMENT '开放平台 unionid（跨公众号/小程序同人）',
  `app_type` varchar(20) NOT NULL DEFAULT 'open' COMMENT 'open=开放平台扫码 mp=小程序',
  `nickname` varchar(64) DEFAULT NULL COMMENT '微信昵称快照',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '微信头像快照',
  `bind_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid_app` (`openid`,`app_type`),
  KEY `idx_account` (`account_id`),
  KEY `idx_unionid` (`unionid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='微信绑定关系';
