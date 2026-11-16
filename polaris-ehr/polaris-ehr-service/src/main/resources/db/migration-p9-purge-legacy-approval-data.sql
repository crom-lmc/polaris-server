-- ============================================================================
-- Polaris EHR P9 清理早期模拟审批数据及字段（MySQL 5.7）
--
-- 口径：
--   1. 未接入 settings 统一审批中心的请假、离职/减员申请连同直接关联数据物理删除。
--   2. 已接入申请保留；审批中、通过、驳回、撤回、终止均视为已接入。
--   3. ehr_leave.approve_note 保留为「提前返岗销假备注」，其余 12 个旧审批列删除。
--
-- 执行前必须：
--   1. 停止 EHR 写入，在测试库验证并备份 ehr、settings 两个库及附件磁盘目录。
--   2. 默认 ehr/settings 位于同一 MySQL 实例，当前库为 ehr；实际库名不同请调整
--      文中所有 `settings`.`appr_instance` 前缀，并以 EHR 库作为当前库。
--   3. 先核对脚本输出的候选主单、附件、交接和员工状态，再执行完整脚本。
--   4. 使用 mysql 客户端默认的错误即停模式，不要加 --force。
--
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 ehr < migration-p9-purge-legacy-approval-data.sql
-- 说明：DML 在一个事务中核对并提交；其后的 ALTER TABLE 会隐式提交，不属于该事务。
--       附件磁盘文件不由本脚本删除；脚本只删除确认无剩余引用的附件主记录。
-- ============================================================================
SET NAMES utf8mb4;

-- 条件断言：任一安全闸门或删除计数不满足即报错，mysql 默认停止执行。
DROP PROCEDURE IF EXISTS `ehr_p9_assert`;
DELIMITER $$
CREATE PROCEDURE `ehr_p9_assert`(IN p_ok BIGINT, IN p_message VARCHAR(128))
BEGIN
  IF p_ok IS NULL OR p_ok <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = p_message;
  END IF;
END$$
DELIMITER ;

-- 跨库不可用会在此处直接报错；统一审批实例异常为 0 时禁止继续，防止误删全部历史单。
SELECT DATABASE() AS `current_ehr_database`;
SELECT COUNT(*) INTO @p9_settings_instances
FROM `settings`.`appr_instance`
WHERE `deleted` = 0 AND `biz_type` IN ('ehr.leave', 'ehr.resign', 'ehr.reduce');
SELECT @p9_settings_instances AS `settings_ehr_instance_count`;
CALL `ehr_p9_assert`(@p9_settings_instances > 0, 'P9 stopped: no EHR approval instances in settings');

-- ----------------------------------------------------------------------------
-- 第一阶段：生成候选 ID 并输出人工核对结果（不依赖业务表 approval_instance_id）
-- ----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_leave`;
CREATE TEMPORARY TABLE `tmp_p9_leave` (
  `id` bigint(20) NOT NULL,
  `employee_id` bigint(20) NOT NULL,
  `status` tinyint(4) NOT NULL,
  `attachment_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MEMORY;
INSERT INTO `tmp_p9_leave` (`id`, `employee_id`, `status`, `attachment_id`)
SELECT l.`id`, l.`employee_id`, l.`status`, l.`attachment_id`
FROM `ehr_leave` l
WHERE NOT EXISTS (
  SELECT 1 FROM `settings`.`appr_instance` ai
  WHERE ai.`biz_type` = 'ehr.leave'
    AND ai.`biz_id` = CAST(l.`id` AS CHAR)
    AND ai.`deleted` = 0
);

DROP TEMPORARY TABLE IF EXISTS `tmp_p9_resign`;
CREATE TEMPORARY TABLE `tmp_p9_resign` (
  `id` bigint(20) NOT NULL,
  `employee_id` bigint(20) NOT NULL,
  `status` tinyint(4) NOT NULL,
  `prev_status` tinyint(4) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MEMORY;
INSERT INTO `tmp_p9_resign` (`id`, `employee_id`, `status`, `prev_status`)
SELECT r.`id`, r.`employee_id`, r.`status`, r.`prev_status`
FROM `ehr_resign_apply` r
WHERE NOT EXISTS (
  SELECT 1 FROM `settings`.`appr_instance` ai
  WHERE ai.`biz_type` IN ('ehr.resign', 'ehr.reduce')
    AND ai.`biz_id` = CAST(r.`id` AS CHAR)
    AND ai.`deleted` = 0
);

DROP TEMPORARY TABLE IF EXISTS `tmp_p9_attachment`;
CREATE TEMPORARY TABLE `tmp_p9_attachment` (
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MEMORY;
INSERT IGNORE INTO `tmp_p9_attachment` (`id`)
SELECT `attachment_id` FROM `tmp_p9_leave` WHERE `attachment_id` IS NOT NULL;
INSERT IGNORE INTO `tmp_p9_attachment` (`id`)
SELECT DISTINCT la.`attachment_id`
FROM `ehr_leave_attachment` la
JOIN `tmp_p9_leave` t ON t.`id` = la.`leave_id`;

-- 只删除绑定到候选请假且不再被其他请假引用的 LEAVE 附件；TEMP 附件保留。
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_attachment_delete`;
CREATE TEMPORARY TABLE `tmp_p9_attachment_delete` (
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=MEMORY;
INSERT INTO `tmp_p9_attachment_delete` (`id`)
SELECT a.`id`
FROM `ehr_attachment` a
JOIN `tmp_p9_attachment` t ON t.`id` = a.`id`
WHERE a.`biz_type` = 'LEAVE'
  AND EXISTS (SELECT 1 FROM `tmp_p9_leave` dl WHERE dl.`id` = a.`biz_id`)
  AND NOT EXISTS (
    SELECT 1
    FROM `ehr_leave_attachment` la
    LEFT JOIN `tmp_p9_leave` dl ON dl.`id` = la.`leave_id`
    WHERE la.`attachment_id` = a.`id` AND dl.`id` IS NULL
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `ehr_leave` l
    LEFT JOIN `tmp_p9_leave` dl ON dl.`id` = l.`id`
    WHERE l.`attachment_id` = a.`id` AND dl.`id` IS NULL
  );

SELECT 'LEAVE' AS `candidate_type`, l.`id`, l.`employee_id`, l.`applicant_name`,
       l.`status`, l.`start_date`, l.`end_date`, l.`reason`
FROM `ehr_leave` l JOIN `tmp_p9_leave` t ON t.`id` = l.`id`
ORDER BY l.`id`;
SELECT 'RESIGN' AS `candidate_type`, r.`id`, r.`employee_id`, r.`employee_name`,
       r.`source`, r.`status`, r.`prev_status`, r.`resign_date`, r.`reason`
FROM `ehr_resign_apply` r JOIN `tmp_p9_resign` t ON t.`id` = r.`id`
ORDER BY r.`id`;
SELECT hi.`apply_id`, hi.`id` AS `handover_item_id`, hi.`employee_id`, hi.`content`, hi.`status`
FROM `ehr_handover_item` hi JOIN `tmp_p9_resign` t ON t.`id` = hi.`apply_id`
ORDER BY hi.`apply_id`, hi.`seq`, hi.`id`;
SELECT a.`id` AS `attachment_id`, a.`biz_type`, a.`biz_id`, a.`file_name`, a.`file_key`,
       CASE WHEN d.`id` IS NULL THEN 0 ELSE 1 END AS `delete_db_record`
FROM `ehr_attachment` a
JOIN `tmp_p9_attachment` t ON t.`id` = a.`id`
LEFT JOIN `tmp_p9_attachment_delete` d ON d.`id` = a.`id`
ORDER BY a.`id`;
SELECT r.`id` AS `apply_id`, e.`id` AS `employee_id`, e.`name`, e.`status` AS `employee_status`,
       r.`status` AS `apply_status`, r.`prev_status`, e.`expected_regularize_date`
FROM `tmp_p9_resign` r
JOIN `ehr_employee` e ON e.`id` = r.`employee_id`
ORDER BY r.`id`;

-- ----------------------------------------------------------------------------
-- 第二阶段：额度/考勤安全闸门。存在引用时输出清单并停止，不擅自冲正或重算。
-- ----------------------------------------------------------------------------
SELECT ql.`leave_id`, ql.`id` AS `quota_log_id`, ql.`quota_id`, ql.`action`,
       ql.`days`, ql.`biz_key`, ql.`created_at`
FROM `ehr_leave_quota_log` ql
JOIN `tmp_p9_leave` t ON t.`id` = ql.`leave_id`
ORDER BY ql.`leave_id`, ql.`id`;
SELECT ad.`leave_id`, ad.`id` AS `attendance_daily_id`, ad.`employee_id`, ad.`work_date`,
       ad.`status`, ad.`deleted`
FROM `ehr_attendance_daily` ad
JOIN `tmp_p9_leave` t ON t.`id` = ad.`leave_id`
ORDER BY ad.`leave_id`, ad.`id`;

SELECT COUNT(*) INTO @p9_quota_refs
FROM `ehr_leave_quota_log` ql JOIN `tmp_p9_leave` t ON t.`id` = ql.`leave_id`;
SELECT COUNT(*) INTO @p9_attendance_refs
FROM `ehr_attendance_daily` ad JOIN `tmp_p9_leave` t ON t.`id` = ad.`leave_id`;
SELECT @p9_quota_refs AS `blocking_quota_refs`, @p9_attendance_refs AS `blocking_attendance_refs`;
CALL `ehr_p9_assert`(@p9_quota_refs = 0, 'P9 stopped: candidate leaves have quota logs');
CALL `ehr_p9_assert`(@p9_attendance_refs = 0, 'P9 stopped: candidate leaves have attendance refs');

-- 仅恢复未通过且删除后没有其他有效离职单的「离职中」档案；已通过/已离职不恢复。
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_employee_restore`;
CREATE TEMPORARY TABLE `tmp_p9_employee_restore` (
  `employee_id` bigint(20) NOT NULL,
  `restore_status` tinyint(4) NOT NULL,
  PRIMARY KEY (`employee_id`)
) ENGINE=MEMORY;
INSERT INTO `tmp_p9_employee_restore` (`employee_id`, `restore_status`)
SELECT e.`id`,
       CASE
         WHEN MAX(r.`prev_status`) IN (30, 40) THEN MAX(r.`prev_status`)
         WHEN e.`expected_regularize_date` IS NOT NULL AND e.`expected_regularize_date` > CURDATE() THEN 30
         ELSE 40
       END
FROM `ehr_employee` e
JOIN `tmp_p9_resign` r ON r.`employee_id` = e.`id` AND r.`status` IN (0, 2, 3, 4)
WHERE e.`status` = 50
  AND NOT EXISTS (
    SELECT 1 FROM `tmp_p9_resign` approved
    WHERE approved.`employee_id` = e.`id` AND approved.`status` = 1
  )
  AND NOT EXISTS (
    SELECT 1
    FROM `ehr_resign_apply` other
    LEFT JOIN `tmp_p9_resign` deleted ON deleted.`id` = other.`id`
    WHERE other.`employee_id` = e.`id`
      AND deleted.`id` IS NULL
      AND other.`status` IN (0, 1, 4)
  )
GROUP BY e.`id`, e.`expected_regularize_date`;

SELECT er.`employee_id`, e.`name`, e.`status` AS `before_status`, er.`restore_status`
FROM `tmp_p9_employee_restore` er JOIN `ehr_employee` e ON e.`id` = er.`employee_id`;

SELECT COUNT(*) INTO @p9_expected_leave_attach
FROM `ehr_leave_attachment` la JOIN `tmp_p9_leave` t ON t.`id` = la.`leave_id`;
SELECT COUNT(*) INTO @p9_expected_attach FROM `tmp_p9_attachment_delete`;
SELECT COUNT(*) INTO @p9_expected_handover
FROM `ehr_handover_item` hi JOIN `tmp_p9_resign` t ON t.`id` = hi.`apply_id`;
SELECT COUNT(*) INTO @p9_expected_leave FROM `tmp_p9_leave`;
SELECT COUNT(*) INTO @p9_expected_resign FROM `tmp_p9_resign`;
SELECT @p9_expected_leave AS `expected_leave`, @p9_expected_resign AS `expected_resign`,
       @p9_expected_leave_attach AS `expected_leave_attachment`, @p9_expected_attach AS `expected_attachment`,
       @p9_expected_handover AS `expected_handover`;

-- ----------------------------------------------------------------------------
-- 第三阶段：事务删除并核对行数；断言失败时不要继续 COMMIT。
-- ----------------------------------------------------------------------------
START TRANSACTION;

DELETE la
FROM `ehr_leave_attachment` la JOIN `tmp_p9_leave` t ON t.`id` = la.`leave_id`;
SET @p9_deleted_leave_attach = ROW_COUNT();

DELETE a
FROM `ehr_attachment` a JOIN `tmp_p9_attachment_delete` t ON t.`id` = a.`id`;
SET @p9_deleted_attach = ROW_COUNT();

DELETE hi
FROM `ehr_handover_item` hi JOIN `tmp_p9_resign` t ON t.`id` = hi.`apply_id`;
SET @p9_deleted_handover = ROW_COUNT();

UPDATE `ehr_employee` e
JOIN `tmp_p9_employee_restore` r ON r.`employee_id` = e.`id`
SET e.`status` = r.`restore_status`
WHERE e.`status` = 50;

DELETE r
FROM `ehr_resign_apply` r JOIN `tmp_p9_resign` t ON t.`id` = r.`id`;
SET @p9_deleted_resign = ROW_COUNT();

DELETE l
FROM `ehr_leave` l JOIN `tmp_p9_leave` t ON t.`id` = l.`id`;
SET @p9_deleted_leave = ROW_COUNT();

SELECT @p9_deleted_leave AS `deleted_leave`, @p9_deleted_resign AS `deleted_resign`,
       @p9_deleted_leave_attach AS `deleted_leave_attachment`, @p9_deleted_attach AS `deleted_attachment`,
       @p9_deleted_handover AS `deleted_handover`;
CALL `ehr_p9_assert`(
  @p9_deleted_leave = @p9_expected_leave
  AND @p9_deleted_resign = @p9_expected_resign
  AND @p9_deleted_leave_attach = @p9_expected_leave_attach
  AND @p9_deleted_attach = @p9_expected_attach
  AND @p9_deleted_handover = @p9_expected_handover,
  'P9 delete count mismatch: rollback required'
);
COMMIT;

-- ----------------------------------------------------------------------------
-- 第四阶段：幂等结构迁移。以下 DDL 自动提交，可重复执行。
-- ----------------------------------------------------------------------------
SET @i := (SELECT COUNT(*) FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_resign_apply'
             AND INDEX_NAME = 'idx_resign_approver');
SET @ddl := IF(@i = 0, 'DO 0', 'ALTER TABLE `ehr_resign_apply` DROP KEY `idx_resign_approver`');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SELECT GROUP_CONCAT(CONCAT('DROP COLUMN `', COLUMN_NAME, '`') SEPARATOR ', ')
INTO @p9_leave_drops
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave'
  AND COLUMN_NAME IN ('approver_account_id', 'approver_name', 'approved_at');
SET @ddl := IF(@p9_leave_drops IS NULL, 'DO 0',
               CONCAT('ALTER TABLE `ehr_leave` ', @p9_leave_drops));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_leave' AND COLUMN_NAME = 'approve_note');
SET @ddl := IF(@c = 0, 'DO 0',
  'ALTER TABLE `ehr_leave` MODIFY COLUMN `approve_note` varchar(255) DEFAULT '''' COMMENT ''提前返岗销假备注''');
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

SELECT GROUP_CONCAT(CONCAT('DROP COLUMN `', COLUMN_NAME, '`') SEPARATOR ', ')
INTO @p9_resign_drops
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ehr_resign_apply'
  AND COLUMN_NAME IN (
    'approver_account_id', 'approver_name', 'approver_org_name', 'approve_note', 'approved_at',
    'hr_approver_account_id', 'hr_approver_name', 'hr_approve_note', 'hr_approved_at'
  );
SET @ddl := IF(@p9_resign_drops IS NULL, 'DO 0',
               CONCAT('ALTER TABLE `ehr_resign_apply` ', @p9_resign_drops));
PREPARE s FROM @ddl; EXECUTE s; DEALLOCATE PREPARE s;

DROP PROCEDURE IF EXISTS `ehr_p9_assert`;
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_employee_restore`;
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_attachment_delete`;
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_attachment`;
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_resign`;
DROP TEMPORARY TABLE IF EXISTS `tmp_p9_leave`;

SELECT 'P9 completed: legacy approval data and columns removed' AS `result`;
