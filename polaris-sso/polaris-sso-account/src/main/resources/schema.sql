-- ACCOUNT 全量结构基线（MySQL 5.7）
-- 2026-11-16 从本地 sso_ms 数据库导出；不含过程备份表。
-- 可重复执行，不包含 DROP/ALTER，不用于存量库升级。

/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_account` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `username` varchar(64) NOT NULL COMMENT '登录名',
  `nickname` varchar(64) DEFAULT '' COMMENT '昵称',
  `email` varchar(128) DEFAULT NULL COMMENT '邮箱（为空存 NULL，uk_email 允许多个 NULL）',
  `data_scope_org_code` text COMMENT '数据权限根组织编码，多个编码使用英文逗号分隔',
  `phone` varchar(32) DEFAULT '' COMMENT '手机号',
  `password_hash` varchar(128) NOT NULL COMMENT 'BCrypt 密码哈希',
  `avatar_url` varchar(255) DEFAULT '' COMMENT '头像',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1正常 2禁用 3锁定 4未激活',
  `source` varchar(32) DEFAULT 'local' COMMENT '来源：local/email/wx 等',
  `pwd_reset_required` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1=下次登录强制改密',
  `login_fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '连续登录失败次数',
  `locked_until` datetime DEFAULT NULL COMMENT '锁定到期时间',
  `last_login_at` datetime DEFAULT NULL COMMENT '最近登录时间',
  `user_id` bigint(20) DEFAULT NULL COMMENT '关联 HR 用户ID（人员），账号归属人',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账号表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_account_app` (
  `account_id` bigint(20) NOT NULL,
  `app_id` bigint(20) NOT NULL,
  PRIMARY KEY (`account_id`,`app_id`),
  KEY `idx_app` (`app_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账号-应用授权';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_account_role` (
  `account_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`account_id`,`role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='账号-角色关联';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
  `redirect_uri` varchar(512) NOT NULL DEFAULT '' COMMENT 'OAuth2 回调地址',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='应用注册表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
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
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_email_code` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `email` varchar(128) NOT NULL,
  `code_hash` varchar(64) NOT NULL COMMENT '楠岃瘉鐮� BCrypt 鍝堝笇锛岀姝㈡槑鏂�',
  `scene` varchar(20) NOT NULL DEFAULT 'login' COMMENT 'login=閭鐧诲綍楠岃瘉鐮�',
  `ip` varchar(45) DEFAULT NULL,
  `expires_at` datetime NOT NULL COMMENT '杩囨湡鏃堕棿',
  `used` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0=鏈娇鐢� 1=宸蹭娇鐢�',
  `try_count` int(11) NOT NULL DEFAULT '0' COMMENT '鏍￠獙灏濊瘯娆℃暟',
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_email_scene` (`email`,`scene`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='邮箱验证码（含哈希与过期）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_login_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_id` bigint(20) DEFAULT NULL COMMENT '鎴愬姛鏃跺～璐︽埛ID锛岀敤鎴蜂笉瀛樺湪绛夊け璐ュ満鏅负 NULL',
  `username` varchar(64) NOT NULL COMMENT '灏濊瘯鐧诲綍鐨勭敤鎴峰悕锛堝嵆浣夸笉瀛樺湪涔熻褰曪紝渚夸簬鎺掓煡鎾炲簱锛�',
  `login_type` varchar(20) NOT NULL DEFAULT 'password' COMMENT 'password/email/wechat',
  `result` varchar(10) NOT NULL COMMENT 'success / fail',
  `fail_reason` varchar(100) DEFAULT NULL COMMENT '澶辫触鍘熷洜锛堝瘑鐮侀敊璇�/璐︽埛閿佸畾/鏈厤缃瓑锛�',
  `client_ip` varchar(45) DEFAULT NULL,
  `user_agent` varchar(512) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_account_time` (`account_id`,`created_at`),
  KEY `idx_username_time` (`username`,`created_at`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='登录审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_permission` (
  `id` bigint(20) NOT NULL,
  `code` varchar(128) NOT NULL COMMENT '权限码，如 account:create（按钮级）',
  `name` varchar(64) NOT NULL COMMENT '权限名称',
  `type` tinyint(4) NOT NULL DEFAULT '2' COMMENT '1=菜单 2=按钮',
  `parent_id` bigint(20) DEFAULT '0' COMMENT '上级权限ID（菜单树）',
  `sort` int(11) NOT NULL DEFAULT '0' COMMENT '排序',
  `remark` varchar(255) DEFAULT '' COMMENT '备注\r',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `app_code` varchar(64) NOT NULL DEFAULT 'sso' COMMENT '所属应用编码；sso 表示平台自身',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perm_app_code` (`app_code`,`code`),
  KEY `idx_perm_app` (`app_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='权限表（菜单+按钮）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_role` (
  `id` bigint(20) NOT NULL,
  `code` varchar(64) NOT NULL COMMENT '角色编码，如 SUPER_ADMIN',
  `app_code` varchar(32) NOT NULL DEFAULT 'sso' COMMENT '所属应用编码',
  `name` varchar(64) NOT NULL COMMENT '角色名称',
  `remark` varchar(255) DEFAULT '' COMMENT '备注\r',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`),
  KEY `idx_role_app` (`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_role_permission` (
  `role_id` bigint(20) NOT NULL,
  `permission_id` bigint(20) NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `idx_perm` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='角色-权限关联';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE IF NOT EXISTS `sso_wechat_bind` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_id` bigint(20) NOT NULL COMMENT '关联账户ID',
  `openid` varchar(64) NOT NULL COMMENT '寰俊 openid锛堝悓涓� app_type 鍞竴锛�',
  `unionid` varchar(64) DEFAULT NULL COMMENT '寮�鏀惧钩鍙� unionid锛堣法鍏紬鍙�/灏忕▼搴忓悓浜猴級',
  `app_type` varchar(20) NOT NULL DEFAULT 'open' COMMENT 'open=寮�鏀惧钩鍙版壂鐮� mp=灏忕▼搴�',
  `nickname` varchar(64) DEFAULT NULL COMMENT '寰俊鏄电О蹇収',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '寰俊澶村儚蹇収',
  `bind_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid_app` (`openid`,`app_type`),
  KEY `idx_account` (`account_id`),
  KEY `idx_unionid` (`unionid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='微信绑定关系';
/*!40101 SET character_set_client = @saved_cs_client */;
