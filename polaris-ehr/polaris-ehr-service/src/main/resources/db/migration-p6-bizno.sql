-- ============================================================================
-- Polaris EHR P6 业务单号统一化（MySQL 5.7）
-- 适用：已存在旧版 ehr 库，为各业务表补「单号」列 + 唯一索引 + 历史数据回填
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 ehr < migration-p6-bizno.sql
-- 说明：单号统一由配置中心「单号规则」(settings.cfg_code_rule) 生成，
--       规则格式 {prefix}-{date}-{seq}，日期 yyyyMMdd，流水 6 位；
--       历史数据回填为 {PREFIX}-LEGACY-{id}，保证唯一且可识别。
--       脚本可重复执行（列/索引均先判断是否存在）。
-- ============================================================================
SET NAMES utf8mb4;

-- ----------------------------------------------------------------------------
-- 1. 加列
-- ----------------------------------------------------------------------------
SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave' AND COLUMN_NAME = 'leave_no');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_leave` ADD COLUMN `leave_no` varchar(32) DEFAULT NULL COMMENT ''请假单号（配置中心规则生成）''',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_overtime' AND COLUMN_NAME = 'overtime_no');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_overtime` ADD COLUMN `overtime_no` varchar(32) DEFAULT NULL COMMENT ''加班单号（配置中心规则生成）''',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave_quota_adjust' AND COLUMN_NAME = 'adjust_no');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_leave_quota_adjust` ADD COLUMN `adjust_no` varchar(32) DEFAULT NULL COMMENT ''额度调整单号（配置中心规则生成）''',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_candidate' AND COLUMN_NAME = 'candidate_no');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_candidate` ADD COLUMN `candidate_no` varchar(32) DEFAULT NULL COMMENT ''候选人编号（配置中心规则生成）''',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_offer' AND COLUMN_NAME = 'offer_no');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_offer` ADD COLUMN `offer_no` varchar(32) DEFAULT NULL COMMENT ''Offer 单号（配置中心规则生成）''',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_attendance_correction' AND COLUMN_NAME = 'correction_no');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_attendance_correction` ADD COLUMN `correction_no` varchar(32) DEFAULT NULL COMMENT ''补卡单号（配置中心规则生成）''',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_candidate_comm' AND COLUMN_NAME = 'comm_no');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_candidate_comm` ADD COLUMN `comm_no` varchar(32) DEFAULT NULL COMMENT ''沟通记录单号（配置中心规则生成）''',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

-- 招聘职位已有 job_no（旧格式 JOB-yyyy-序号），无需加列

-- ----------------------------------------------------------------------------
-- 2. 历史数据回填
-- ----------------------------------------------------------------------------
UPDATE `ehr_leave` SET `leave_no` = CONCAT('LV-LEGACY-', `id`)
WHERE `leave_no` IS NULL OR `leave_no` = '';
UPDATE `ehr_overtime` SET `overtime_no` = CONCAT('OT-LEGACY-', `id`)
WHERE `overtime_no` IS NULL OR `overtime_no` = '';
UPDATE `ehr_leave_quota_adjust` SET `adjust_no` = CONCAT('QA-LEGACY-', `id`)
WHERE `adjust_no` IS NULL OR `adjust_no` = '';
UPDATE `ehr_candidate` SET `candidate_no` = CONCAT('CD-LEGACY-', `id`)
WHERE `candidate_no` IS NULL OR `candidate_no` = '';
UPDATE `ehr_offer` SET `offer_no` = CONCAT('OF-LEGACY-', `id`)
WHERE `offer_no` IS NULL OR `offer_no` = '';
UPDATE `ehr_attendance_correction` SET `correction_no` = CONCAT('BC-LEGACY-', `id`)
WHERE `correction_no` IS NULL OR `correction_no` = '';
UPDATE `ehr_candidate_comm` SET `comm_no` = CONCAT('CM-LEGACY-', `id`)
WHERE `comm_no` IS NULL OR `comm_no` = '';

-- ----------------------------------------------------------------------------
-- 3. 唯一索引
-- ----------------------------------------------------------------------------
SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave' AND INDEX_NAME = 'uk_leave_no');
SET @ddl := IF(@i = 0, 'ALTER TABLE `ehr_leave` ADD UNIQUE KEY `uk_leave_no` (`leave_no`)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_overtime' AND INDEX_NAME = 'uk_overtime_no');
SET @ddl := IF(@i = 0, 'ALTER TABLE `ehr_overtime` ADD UNIQUE KEY `uk_overtime_no` (`overtime_no`)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave_quota_adjust' AND INDEX_NAME = 'uk_adjust_no');
SET @ddl := IF(@i = 0, 'ALTER TABLE `ehr_leave_quota_adjust` ADD UNIQUE KEY `uk_adjust_no` (`adjust_no`)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_candidate' AND INDEX_NAME = 'uk_candidate_no');
SET @ddl := IF(@i = 0, 'ALTER TABLE `ehr_candidate` ADD UNIQUE KEY `uk_candidate_no` (`candidate_no`)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_offer' AND INDEX_NAME = 'uk_offer_no');
SET @ddl := IF(@i = 0, 'ALTER TABLE `ehr_offer` ADD UNIQUE KEY `uk_offer_no` (`offer_no`)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_attendance_correction' AND INDEX_NAME = 'uk_correction_no');
SET @ddl := IF(@i = 0, 'ALTER TABLE `ehr_attendance_correction` ADD UNIQUE KEY `uk_correction_no` (`correction_no`)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_candidate_comm' AND INDEX_NAME = 'uk_comm_no');
SET @ddl := IF(@i = 0, 'ALTER TABLE `ehr_candidate_comm` ADD UNIQUE KEY `uk_comm_no` (`comm_no`)', 'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;
