-- 接口开放平台结构基线（MySQL 5.7，独立库 polaris_openapi）
-- 可重复执行：全部 CREATE TABLE IF NOT EXISTS，不含 DROP/ALTER。

CREATE TABLE IF NOT EXISTS `api_open_endpoint` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `api_code` varchar(64) NOT NULL COMMENT '开放API唯一标识，如 inbound.master.list',
  `api_name` varchar(128) NOT NULL COMMENT 'API名称',
  `direction` tinyint(4) NOT NULL COMMENT '1=INBOUND 2=OUTBOUND',
  `app_code` varchar(64) NOT NULL COMMENT '目标内部应用编码 / 出站渠道编码',
  `method` varchar(16) NOT NULL DEFAULT 'POST' COMMENT 'HTTP方法',
  `path` varchar(255) NOT NULL COMMENT '对外路径',
  `target_url` varchar(512) NOT NULL DEFAULT '' COMMENT '内部转发地址',
  `auth_type` varchar(32) NOT NULL DEFAULT 'OAUTH_CLIENT' COMMENT 'OAUTH_CLIENT/SIGNATURE/INTERNAL',
  `required_perm` varchar(128) NOT NULL DEFAULT '' COMMENT '需要的权限码（sso_permission.code）',
  `rate_limit_per_min` int(11) NOT NULL DEFAULT '60' COMMENT '每分钟调用上限',
  `timeout_ms` int(11) NOT NULL DEFAULT '3000' COMMENT '超时（毫秒）',
  `retry_max` int(11) NOT NULL DEFAULT '0' COMMENT '最大重试次数（仅幂等接口）',
  `idempotent` tinyint(4) NOT NULL DEFAULT '0' COMMENT '1=幂等，允许重试',
  `mask_rules` text COMMENT '脱敏字段规则 JSON',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 2停用',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_code` (`api_code`),
  KEY `idx_dir_app` (`direction`,`app_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='开放API目录';

CREATE TABLE IF NOT EXISTS `api_call_log` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `trace_id` varchar(64) NOT NULL COMMENT '全链路追踪ID',
  `direction` varchar(16) NOT NULL COMMENT 'INBOUND / OUTBOUND',
  `app_code` varchar(64) NOT NULL DEFAULT '' COMMENT '调用方应用（进）/ 目标渠道（出）',
  `api_code` varchar(64) NOT NULL DEFAULT '' COMMENT '匹配的开放API标识',
  `channel` varchar(64) NOT NULL DEFAULT '' COMMENT '出站渠道编码',
  `http_method` varchar(16) NOT NULL DEFAULT '',
  `request_path` varchar(512) NOT NULL DEFAULT '',
  `req_masked` mediumtext COMMENT '脱敏后的请求报文（超长截断）',
  `resp_masked` mediumtext COMMENT '脱敏后的响应报文（超长截断）',
  `http_status` int(11) NOT NULL DEFAULT '0' COMMENT 'HTTP状态码',
  `biz_code` varchar(64) NOT NULL DEFAULT '' COMMENT '业务错误码',
  `cost_ms` int(11) NOT NULL DEFAULT '0' COMMENT '耗时（毫秒）',
  `client_ip` varchar(64) NOT NULL DEFAULT '' COMMENT '来源IP',
  `error_msg` varchar(512) NOT NULL DEFAULT '' COMMENT '异常信息',
  `created_at` datetime NOT NULL COMMENT '调用时间',
  PRIMARY KEY (`id`),
  KEY `idx_trace` (`trace_id`),
  KEY `idx_app_time` (`app_code`,`created_at`),
  KEY `idx_api_time` (`api_code`,`created_at`),
  KEY `idx_dir_time` (`direction`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='接口调用日志（进出统一）';

-- P2 平台设置（KV）：IP 白名单、全局限流、nonce 有效期、脱敏规则等
CREATE TABLE IF NOT EXISTS `api_open_setting` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `setting_key` varchar(64) NOT NULL COMMENT '配置键',
  `setting_value` text COMMENT '配置值（IP 白名单为换行分隔的 IP/CIDR/*）',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_setting_key` (`setting_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='开放平台设置(KV)';

CREATE TABLE IF NOT EXISTS `api_outbound_channel` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `channel_code` varchar(64) NOT NULL COMMENT '渠道编码，如 email',
  `channel_name` varchar(128) NOT NULL COMMENT '渠道名称',
  `base_url` varchar(512) NOT NULL DEFAULT '' COMMENT '第三方 base-url（邮件渠道可留空）',
  `auth_type` varchar(32) NOT NULL DEFAULT 'API_KEY' COMMENT 'API_KEY / OAUTH / SIGNATURE / NONE',
  `credential_ref` varchar(512) NOT NULL DEFAULT '' COMMENT '凭证密文 ENC(base64)，AES-GCM 加密的 SMTP 账号/口令 JSON；明文绝不入库、不回显',
  `auth_config` text COMMENT '认证附加配置 JSON（邮件渠道存 host/port/from/ssl 等非敏感项）',
  `timeout_ms` int(11) NOT NULL DEFAULT '3000' COMMENT '超时（毫秒）',
  `retry_max` int(11) NOT NULL DEFAULT '0' COMMENT '最大重试次数',
  `allowed_apps` text COMMENT '允许哪些内部 app 使用，逗号分隔；空=不限制',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1启用 2停用',
  `remark` varchar(255) NOT NULL DEFAULT '' COMMENT '备注',
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='出站第三方渠道配置';
