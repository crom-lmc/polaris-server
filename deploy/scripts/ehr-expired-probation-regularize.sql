-- EHR 已到期试用期员工批量转正（逐条顺序执行版）
-- 请在 EHR 数据库的独立 SQL 窗口执行（本地默认数据库：ehr）。
--
-- 处理范围同时满足：
--   1. 未逻辑删除，当前状态 status=30（试用期）；
--   2. 非免试用期，hire_date 不为空，probation_months 为 1-12；
--   3. DATE_ADD(hire_date, INTERVAL probation_months MONTH) <= 执行当天；
--   4. 没有审批中的转正申请，且 regularize_status 不为 1/2。
--
-- 更新内容：
--   expected_regularize_date = hire_date + probation_months 个月；
--   status = 40（正式在职）；
--   regularize_status = 3（已通过/已转正）。
--
-- 注意：
--   1. 这是历史数据修正，直接更新员工档案，不生成转正申请单或审批记录。
--   2. 本脚本不使用临时表和显式事务，每条语句独立自动提交，适配逐条顺序执行。
--   3. 请务必先执行备份 INSERT，再执行 UPDATE；不要在其他有未提交事务的 SQL 窗口执行。

-- 使用独立自动提交，避免漏执行 COMMIT 或关闭窗口后回滚。
SET SESSION autocommit = 1;

-- 第 1 步：执行前预览，本地当前应返回 15 人。
SELECT
  e.id AS employee_id,
  e.emp_no,
  e.name,
  e.hire_date,
  e.probation_months,
  e.expected_regularize_date AS old_expected_regularize_date,
  DATE_ADD(e.hire_date, INTERVAL e.probation_months MONTH) AS new_expected_regularize_date,
  e.status AS old_status,
  40 AS new_status,
  e.regularize_status AS old_regularize_status,
  3 AS new_regularize_status
FROM ehr_employee e
WHERE e.deleted = 0
  AND e.status = 30
  AND COALESCE(e.exempt_probation, 0) = 0
  AND e.hire_date IS NOT NULL
  AND e.probation_months BETWEEN 1 AND 12
  AND DATE_ADD(e.hire_date, INTERVAL e.probation_months MONTH) <= CURDATE()
  AND COALESCE(e.regularize_status, 0) NOT IN (1, 2)
  AND NOT EXISTS (
    SELECT 1
    FROM ehr_regularize_apply ra
    WHERE ra.employee_id = e.id
      AND ra.deleted = 0
      AND ra.status = 0
  )
ORDER BY new_expected_regularize_date, e.id;

-- 第 2 步：创建备份表；已存在时不会重建。
CREATE TABLE IF NOT EXISTS backup_ehr_expired_probation_20261116
LIKE ehr_employee;

-- 第 3 步：备份目标员工原始整行数据；重复执行不会重复备份同一员工。
INSERT IGNORE INTO backup_ehr_expired_probation_20261116
SELECT e.*
FROM ehr_employee e
WHERE e.deleted = 0
  AND e.status = 30
  AND COALESCE(e.exempt_probation, 0) = 0
  AND e.hire_date IS NOT NULL
  AND e.probation_months BETWEEN 1 AND 12
  AND DATE_ADD(e.hire_date, INTERVAL e.probation_months MONTH) <= CURDATE()
  AND COALESCE(e.regularize_status, 0) NOT IN (1, 2)
  AND NOT EXISTS (
    SELECT 1
    FROM ehr_regularize_apply ra
    WHERE ra.employee_id = e.id
      AND ra.deleted = 0
      AND ra.status = 0
  );

-- 本次新备份行数；本地首次执行应为 15。
SELECT ROW_COUNT() AS backup_inserted_rows;

-- 第 4 步：确认上一步备份行数符合预期后，执行正式更新。
UPDATE ehr_employee e
SET
  e.expected_regularize_date = DATE_ADD(e.hire_date, INTERVAL e.probation_months MONTH),
  e.status = 40,
  e.regularize_status = 3,
  e.updated_at = NOW()
WHERE e.deleted = 0
  AND e.status = 30
  AND COALESCE(e.exempt_probation, 0) = 0
  AND e.hire_date IS NOT NULL
  AND e.probation_months BETWEEN 1 AND 12
  AND DATE_ADD(e.hire_date, INTERVAL e.probation_months MONTH) <= CURDATE()
  AND COALESCE(e.regularize_status, 0) NOT IN (1, 2)
  AND NOT EXISTS (
    SELECT 1
    FROM ehr_regularize_apply ra
    WHERE ra.employee_id = e.id
      AND ra.deleted = 0
      AND ra.status = 0
  );

-- 本次实际更新行数；本地首次执行应为 15。
SELECT ROW_COUNT() AS updated_employee_count;

-- 核验 1：正常应为 0，表示符合条件的员工已全部更新。
SELECT COUNT(*) AS remaining_unupdated_count
FROM ehr_employee e
WHERE e.deleted = 0
  AND e.status = 30
  AND COALESCE(e.exempt_probation, 0) = 0
  AND e.hire_date IS NOT NULL
  AND e.probation_months BETWEEN 1 AND 12
  AND DATE_ADD(e.hire_date, INTERVAL e.probation_months MONTH) <= CURDATE()
  AND COALESCE(e.regularize_status, 0) NOT IN (1, 2)
  AND NOT EXISTS (
    SELECT 1
    FROM ehr_regularize_apply ra
    WHERE ra.employee_id = e.id
      AND ra.deleted = 0
      AND ra.status = 0
  );

-- 核验 2：备份表累计保存的原始员工快照数量，本地首次执行后应为 15。
SELECT COUNT(*) AS backup_employee_count
FROM backup_ehr_expired_probation_20261116;

-- 核验 3：查看已经批量转正的人员；首次执行应返回备份表中的 15 人。
SELECT
  e.id AS employee_id,
  e.emp_no,
  e.name,
  e.hire_date,
  e.probation_months,
  e.expected_regularize_date,
  e.status,
  e.regularize_status
FROM backup_ehr_expired_probation_20261116 b
INNER JOIN ehr_employee e ON e.id = b.id
WHERE e.status = 40
  AND e.regularize_status = 3
ORDER BY e.expected_regularize_date, e.id;
