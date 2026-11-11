-- ------------------------------------------------------------
-- Table DDL (idempotent CREATE TABLE IF NOT EXISTS, re-runnable on service start)
-- ------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `job_info` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `app_code` varchar(32) NOT NULL COMMENT '所属应用：ehr/settings/gateway/cms/blog',
  `name` varchar(64) NOT NULL COMMENT '任务名称',
  `handler` varchar(128) NOT NULL COMMENT '应用内 JobHandler#name()',
  `description` varchar(255) DEFAULT '' COMMENT '描述',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'CRON' COMMENT 'CRON/FIXED_RATE/FIXED_DELAY',
  `cron_expr` varchar(64) DEFAULT NULL COMMENT '6 位 Quartz 表达式',
  `interval_sec` int(11) DEFAULT NULL COMMENT '固定频率/延迟秒数',
  `timeout_sec` int(11) NOT NULL DEFAULT '300' COMMENT '超时秒数',
  `retry_times` int(11) NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `retry_interval_sec` int(11) NOT NULL DEFAULT '30' COMMENT '重试间隔秒数',
  `route_strategy` varchar(16) NOT NULL DEFAULT 'ROUND' COMMENT 'ROUND/FAILOVER/RANDOM/FIRST/BROADCAST',
  `block_strategy` varchar(16) NOT NULL DEFAULT 'DISCARD' COMMENT 'DISCARD/SERIAL/COVER',
  `params` text COMMENT '任务参数 JSON 对象',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0 暂停 / 1 运行',
  `next_fire_time` datetime DEFAULT NULL COMMENT '下次触发时间',
  `owner_account_id` bigint(20) DEFAULT NULL COMMENT '负责人账号ID',
  `lock_version` int(11) NOT NULL DEFAULT '0' COMMENT '乐观锁版本',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_job_info_handler` (`app_code`,`handler`),
  KEY `idx_job_info_fire` (`status`,`next_fire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务定义';

CREATE TABLE IF NOT EXISTS `job_registry` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `app_code` varchar(32) NOT NULL COMMENT '应用编码',
  `address` varchar(64) NOT NULL COMMENT '实例地址 host:port',
  `handlers` mediumtext COMMENT '上报的 Handler 清单 JSON',
  `version` varchar(64) DEFAULT '' COMMENT '应用版本',
  `job_count` int(11) NOT NULL DEFAULT '0' COMMENT 'Handler 数量',
  `online` tinyint(4) NOT NULL DEFAULT '1' COMMENT '最近一次上报状态',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '最后心跳时间',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_registry` (`app_code`,`address`),
  KEY `idx_job_registry_hb` (`last_heartbeat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行器注册表';

CREATE TABLE IF NOT EXISTS `job_log` (
  `id` bigint(20) NOT NULL COMMENT '执行ID（雪花）',
  `job_id` bigint(20) NOT NULL COMMENT '任务ID',
  `trigger_type` varchar(16) NOT NULL DEFAULT 'SCHEDULE' COMMENT 'SCHEDULE/MANUAL/RETRY',
  `planned_at` datetime DEFAULT NULL COMMENT '计划触发时间',
  `started_at` datetime DEFAULT NULL COMMENT '实际开始',
  `finished_at` datetime DEFAULT NULL COMMENT '实际结束',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0执行中/1成功/2失败/3超时/4取消',
  `duration_ms` int(11) DEFAULT NULL COMMENT '耗时毫秒',
  `retry_index` int(11) NOT NULL DEFAULT '0' COMMENT '重试序号，0 为首次',
  `executor_address` varchar(64) DEFAULT '' COMMENT '实际执行实例',
  `error_msg` text COMMENT '异常摘要',
  `output` text COMMENT '输出尾部（截断）',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_job_log_job` (`job_id`,`created_at`),
  KEY `idx_job_log_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务执行日志';

CREATE TABLE IF NOT EXISTS `job_alarm` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `name` varchar(64) NOT NULL DEFAULT '' COMMENT '规则名称',
  `scope_type` varchar(16) NOT NULL DEFAULT 'JOB' COMMENT 'JOB/APP/ALL',
  `scope_value` varchar(64) DEFAULT '' COMMENT '任务ID或应用编码',
  `trigger_condition` varchar(32) NOT NULL DEFAULT 'FAIL' COMMENT 'FAIL/TIMEOUT/CONSECUTIVE_FAIL',
  `threshold` int(11) NOT NULL DEFAULT '1' COMMENT '连续失败阈值',
  `channels` varchar(128) NOT NULL DEFAULT 'SITE' COMMENT '通知渠道，逗号分隔',
  `org_ids` varchar(500) DEFAULT '' COMMENT '接收组织ID，逗号分隔',
  `account_ids` varchar(500) DEFAULT '' COMMENT '接收人账号ID，逗号分隔',
  `silence_minutes` int(11) NOT NULL DEFAULT '60' COMMENT '静默期分钟',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '0 停用 / 1 启用',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_job_alarm_scope` (`scope_type`,`scope_value`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警规则';

CREATE TABLE IF NOT EXISTS `job_lock` (
  `id` bigint(20) NOT NULL,
  `lock_name` varchar(64) NOT NULL COMMENT '锁名，调度中心固定 scheduler',
  `owner` varchar(128) NOT NULL DEFAULT '' COMMENT '持有者实例标识 host:port:pid',
  `expire_at` datetime NOT NULL DEFAULT '1970-01-01 00:00:00',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_lock_name` (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调度选主租约';

CREATE TABLE IF NOT EXISTS `job_alarm_log` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `job_id` bigint(20) DEFAULT NULL,
  `log_id` bigint(20) DEFAULT NULL,
  `rule_id` bigint(20) DEFAULT NULL,
  `event` varchar(16) NOT NULL DEFAULT 'FAIL' COMMENT 'FAIL/RECOVER',
  `channel` varchar(32) DEFAULT '' COMMENT '渠道',
  `receivers` varchar(500) DEFAULT '' COMMENT '接收人快照',
  `content` varchar(1000) DEFAULT '' COMMENT '报警内容快照',
  `status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0 待发 / 1 成功 / 2 失败',
  `sent_at` datetime DEFAULT NULL,
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_job_alarm_log_job` (`job_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警流水';

CREATE TABLE IF NOT EXISTS `job_alarm_channel` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `channel` varchar(16) NOT NULL COMMENT 'SITE/EMAIL',
  `enabled` tinyint(4) NOT NULL DEFAULT '0' COMMENT '0 停用 / 1 启用',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_job_alarm_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报警全局通知渠道开关';
