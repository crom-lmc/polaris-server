-- ============================================================================
-- Polaris EHR P5 招聘多轮面试（MySQL 5.7）
-- 适用：已存在旧版 ehr 库，需要新增「面试轮次/反馈」与「候选人沟通留痕」两张表
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 ehr < migration-p5-interview.sql
-- 说明：均为新建表，脚本可重复执行（CREATE TABLE IF NOT EXISTS）
-- ============================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `ehr_interview` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `candidate_id` bigint(20) NOT NULL COMMENT '候选人ID',
  `job_id` bigint(20) DEFAULT NULL COMMENT '招聘职位ID',
  `interview_no` varchar(32) NOT NULL DEFAULT '' COMMENT '面试单号：IV-yyyyMMdd-6位流水',
  `round_no` int(11) NOT NULL DEFAULT '1' COMMENT '第几轮面试，从 1 开始',
  `interviewer_id` bigint(20) NOT NULL COMMENT '面试官员工档案ID',
  `interviewer_account_id` bigint(20) DEFAULT NULL COMMENT '面试官统一账号ID（为空表示未开户，不发待办）',
  `interviewer_name` varchar(64) NOT NULL DEFAULT '' COMMENT '面试官姓名快照',
  `scheduled_at` datetime NOT NULL COMMENT '约定面试时间',
  `method` varchar(16) NOT NULL DEFAULT 'ONSITE' COMMENT 'ONSITE现场/VIDEO视频/PHONE电话',
  `location` varchar(255) DEFAULT '' COMMENT '面试地点或会议链接',
  `status` varchar(16) NOT NULL DEFAULT 'SCHEDULED' COMMENT 'SCHEDULED待面试/DONE已反馈/CANCELED已取消',
  `score` int(11) DEFAULT NULL COMMENT '评分 1-5',
  `feedback` varchar(1000) DEFAULT '' COMMENT '面试反馈',
  `feedback_at` datetime DEFAULT NULL COMMENT '反馈提交时间',
  `round_result` varchar(16) DEFAULT NULL COMMENT 'HR 对该轮判定：PASS通过/FAIL未通过，同轮所有行同步',
  `decided_at` datetime DEFAULT NULL COMMENT '判定时间',
  `decided_by` bigint(20) DEFAULT NULL COMMENT '判定人统一账号ID',
  `remark` varchar(255) DEFAULT '' COMMENT '备注/取消原因',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_interview_no` (`interview_no`),
  KEY `idx_interview_candidate` (`candidate_id`,`round_no`),
  KEY `idx_interview_interviewer` (`interviewer_account_id`,`status`),
  KEY `idx_interview_schedule` (`scheduled_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招聘面试轮次与面试官反馈';

CREATE TABLE IF NOT EXISTS `ehr_candidate_comm` (
  `id` bigint(20) NOT NULL COMMENT '雪花ID',
  `candidate_id` bigint(20) NOT NULL COMMENT '候选人ID',
  `type` varchar(16) NOT NULL DEFAULT 'PHONE' COMMENT 'OFFER_TALK薪资沟通/PHONE电话/OTHER其他',
  `content` varchar(500) NOT NULL COMMENT '沟通内容',
  `expected_salary` decimal(12,2) DEFAULT NULL COMMENT '沟通中确认的期望月薪',
  `occurred_at` datetime NOT NULL COMMENT '沟通时间',
  `operator_id` bigint(20) DEFAULT NULL COMMENT '操作人统一账号ID',
  `operator_name` varchar(64) DEFAULT '' COMMENT '操作人姓名',
  `deleted` tinyint(4) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_comm_candidate` (`candidate_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='HR 与候选人沟通留痕';

-- ----------------------------------------------------------------------------
-- 存量库升级：面试单号（新建库已在建表语句中包含，无需执行）
-- 规则：IV-yyyyMMdd-6位流水，如 IV-20261110-000001
-- ----------------------------------------------------------------------------
SET @col := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_interview'
               AND COLUMN_NAME = 'interview_no');
SET @ddl := IF(@col = 0,
  'ALTER TABLE `ehr_interview` ADD COLUMN `interview_no` varchar(32) NOT NULL DEFAULT '''' COMMENT ''面试单号：IV-yyyyMMdd-6位流水'' AFTER `job_id`',
  'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 存量数据回填（历史记录无单号，按 IV-LEGACY-{id} 生成，保证唯一）
UPDATE `ehr_interview` SET `interview_no` = CONCAT('IV-LEGACY-', `id`)
WHERE `interview_no` = '' OR `interview_no` IS NULL;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_interview'
               AND INDEX_NAME = 'uk_interview_no');
SET @ddl2 := IF(@idx = 0,
  'ALTER TABLE `ehr_interview` ADD UNIQUE KEY `uk_interview_no` (`interview_no`)',
  'DO 0');
PREPARE stmt2 FROM @ddl2;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
