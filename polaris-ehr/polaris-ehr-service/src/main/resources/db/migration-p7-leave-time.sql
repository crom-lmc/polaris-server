-- ============================================================================
-- Polaris EHR P7 请假精确到半小时（MySQL 5.7）
-- 适用：已存在旧版 ehr 库，请假单增加起止时间列并放宽天数精度
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 ehr < migration-p7-leave-time.sql
-- 说明：start_time / end_time 存 HH:mm（按半小时选择）；days 由 1 位小数放宽到 2 位，
--       8 小时为 1 天，半小时折算 0.06 天。脚本可重复执行。
-- ============================================================================
SET NAMES utf8mb4;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave' AND COLUMN_NAME = 'start_time');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_leave` ADD COLUMN `start_time` char(5) DEFAULT NULL COMMENT ''开始时间 HH:mm（精确到半小时）'' AFTER `end_period`',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave' AND COLUMN_NAME = 'end_time');
SET @ddl := IF(@c = 0,
  'ALTER TABLE `ehr_leave` ADD COLUMN `end_time` char(5) DEFAULT NULL COMMENT ''结束时间 HH:mm（精确到半小时）'' AFTER `start_time`',
  'DO 0');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE `ehr_leave`
  MODIFY COLUMN `days` decimal(6,2) NOT NULL COMMENT '请假天数（8 小时/天，半小时折算 0.06 天）';
