-- EHR 员工合同历史数据生成：3 年固定期限 + 3 年固定期限 + 无固定期限
-- 请在 EHR 数据库的独立 SQL 窗口按顺序执行（本地默认数据库：ehr）。
-- 本脚本只新增合同，不修改或删除已有员工、合同数据；兼容 MySQL 5.7+。
--
-- 生成规则（以执行当天 CURDATE() 为基准）：
--   1. 第一份：入职日起 3 年，结束日为入职满 3 年的前一天；
--   2. 第二份：入职满 3 年之日起 3 年，仅在已到第一次续签时间时生成；
--   3. 第三份：入职满 6 年起无固定期限，仅在已到第二次续签时间时生成；
--   4. 已被后续合同接续的固定期限合同状态为 RENEWED（已续签），当前合同为 ACTIVE（生效中）；
--   5. 例如入职已满 10 年：生成两份 3 年 FIXED（均为 RENEWED）和一份 UNFIXED（ACTIVE）。
--
-- 处理范围：
--   1. 未逻辑删除，状态为 30 试用期 / 40 在职 / 50 离职中；
--   2. hire_date 不为空且不晚于执行当天；
--   3. 当前没有任何未逻辑删除合同的员工。已有合同（包括审批中、已到期、已终止）的员工整人跳过。
--
-- 重复执行：首次生成的员工随后已有合同，因此再次执行不会重复插入。
-- 注意：ehr_emp_contract 必须已有 org_code 字段；本脚本不执行表结构变更。

SET SESSION autocommit = 1;

-- 第 1 步：预览将为每位员工生成的合同明细，确认日期、类型和状态。
-- 2026-11-16 只读核验时，本地数据预计为 162 名员工、298 份合同。
SELECT
  employee_id, emp_no, name, hire_date, seq AS contract_seq,
  contract_no, contract_type, start_date, end_date, sign_date, status
FROM (
  SELECT
    e.id AS employee_id,
    e.emp_no,
    e.name,
    e.hire_date,
    s.seq,
    CONCAT('HT-', e.emp_no, '-', LPAD(s.seq, 2, '0')) AS contract_no,
    IF(s.seq = 3, 'UNFIXED', 'FIXED') AS contract_type,
    DATE_ADD(e.hire_date, INTERVAL ((s.seq - 1) * 3) YEAR) AS start_date,
    CASE
      WHEN s.seq = 3 THEN NULL
      ELSE DATE_SUB(DATE_ADD(e.hire_date, INTERVAL (s.seq * 3) YEAR), INTERVAL 1 DAY)
    END AS end_date,
    DATE_ADD(e.hire_date, INTERVAL ((s.seq - 1) * 3) YEAR) AS sign_date,
    IF(
      s.seq < 3 AND DATE_ADD(e.hire_date, INTERVAL (s.seq * 3) YEAR) <= CURDATE(),
      'RENEWED',
      'ACTIVE'
    ) AS status
  FROM (
    SELECT e.id, e.emp_no, e.name, e.hire_date
    FROM ehr_employee e
    WHERE e.deleted = 0
      AND e.status IN (30, 40, 50)
      AND e.hire_date IS NOT NULL
      AND e.hire_date <= CURDATE()
      AND NOT EXISTS (
        SELECT 1
        FROM ehr_emp_contract c
        WHERE c.employee_id = e.id
          AND c.deleted = 0
      )
  ) e
  CROSS JOIN (
    SELECT 1 AS seq UNION ALL SELECT 2 UNION ALL SELECT 3
  ) s
  WHERE s.seq = 1
    OR DATE_ADD(e.hire_date, INTERVAL ((s.seq - 1) * 3) YEAR) <= CURDATE()
) contract_rows
ORDER BY emp_no, contract_seq;

-- 第 2 步：查看因已有合同而整人跳过的员工；这些员工不会生成任何数据。
SELECT
  e.id AS employee_id,
  e.emp_no,
  e.name,
  e.hire_date,
  COUNT(c.id) AS existing_contract_count,
  '已有合同，跳过' AS skip_reason
FROM ehr_employee e
INNER JOIN ehr_emp_contract c
  ON c.employee_id = e.id
 AND c.deleted = 0
WHERE e.deleted = 0
  AND e.status IN (30, 40, 50)
  AND e.hire_date IS NOT NULL
  AND e.hire_date <= CURDATE()
GROUP BY e.id, e.emp_no, e.name, e.hire_date
ORDER BY e.emp_no;

-- 第 3 步：确认预览无误后执行正式插入。
-- ID 根据员工 ID 和合同序号确定性生成；renew_from_id 指向同一员工的前一份合同。
INSERT INTO ehr_emp_contract (
  id, employee_id, org_code, contract_no, contract_type,
  start_date, end_date, sign_date, status, renew_from_id,
  file_id, remark, deleted, created_at, updated_at
)
SELECT
  contract_rows.id,
  contract_rows.employee_id,
  contract_rows.org_code,
  contract_rows.contract_no,
  contract_rows.contract_type,
  contract_rows.start_date,
  contract_rows.end_date,
  contract_rows.sign_date,
  contract_rows.status,
  contract_rows.renew_from_id,
  NULL AS file_id,
  'AUTO_3Y_3Y_UNFIXED_20261116' AS remark,
  0 AS deleted,
  NOW() AS created_at,
  NOW() AS updated_at
FROM (
  SELECT
    CAST(CONV(SUBSTRING(MD5(CONCAT(
      'EHR_CONTRACT_20261116_', e.id, '_', s.seq
    )), 1, 15), 16, 10) AS SIGNED) AS id,
    e.id AS employee_id,
    e.emp_no,
    e.org_code,
    s.seq,
    CONCAT('HT-', e.emp_no, '-', LPAD(s.seq, 2, '0')) AS contract_no,
    IF(s.seq = 3, 'UNFIXED', 'FIXED') AS contract_type,
    DATE_ADD(e.hire_date, INTERVAL ((s.seq - 1) * 3) YEAR) AS start_date,
    CASE
      WHEN s.seq = 3 THEN NULL
      ELSE DATE_SUB(DATE_ADD(e.hire_date, INTERVAL (s.seq * 3) YEAR), INTERVAL 1 DAY)
    END AS end_date,
    DATE_ADD(e.hire_date, INTERVAL ((s.seq - 1) * 3) YEAR) AS sign_date,
    IF(
      s.seq < 3 AND DATE_ADD(e.hire_date, INTERVAL (s.seq * 3) YEAR) <= CURDATE(),
      'RENEWED',
      'ACTIVE'
    ) AS status,
    CASE
      WHEN s.seq = 1 THEN NULL
      ELSE CAST(CONV(SUBSTRING(MD5(CONCAT(
        'EHR_CONTRACT_20261116_', e.id, '_', s.seq - 1
      )), 1, 15), 16, 10) AS SIGNED)
    END AS renew_from_id
  FROM (
    SELECT e.id, e.emp_no, e.hire_date, e.org_code
    FROM ehr_employee e
    WHERE e.deleted = 0
      AND e.status IN (30, 40, 50)
      AND e.hire_date IS NOT NULL
      AND e.hire_date <= CURDATE()
      AND NOT EXISTS (
        SELECT 1
        FROM ehr_emp_contract c
        WHERE c.employee_id = e.id
          AND c.deleted = 0
      )
  ) e
  CROSS JOIN (
    SELECT 1 AS seq UNION ALL SELECT 2 UNION ALL SELECT 3
  ) s
  WHERE s.seq = 1
    OR DATE_ADD(e.hire_date, INTERVAL ((s.seq - 1) * 3) YEAR) <= CURDATE()
) contract_rows
ORDER BY contract_rows.emp_no, contract_rows.seq;

-- 本次实际新增合同数；若员工数据未变化，本地首次执行应为 298。
SELECT ROW_COUNT() AS inserted_contract_rows;

-- 第 4 步：按类型和状态核验本脚本生成的合同。
SELECT contract_type, status, COUNT(*) AS contract_count
FROM ehr_emp_contract
WHERE deleted = 0
  AND remark = 'AUTO_3Y_3Y_UNFIXED_20261116'
GROUP BY contract_type, status
ORDER BY contract_type, status;

-- 核验：每位生成员工应且仅应有一份 ACTIVE 合同；正常应返回 0 行。
SELECT employee_id, COUNT(*) AS active_contract_count
FROM ehr_emp_contract
WHERE deleted = 0
  AND status = 'ACTIVE'
  AND remark = 'AUTO_3Y_3Y_UNFIXED_20261116'
GROUP BY employee_id
HAVING COUNT(*) <> 1;

-- 第 5 步：查看生成结果；入职满 10 年的员工应显示两份 FIXED + 一份 UNFIXED。
SELECT
  e.emp_no,
  e.name,
  e.hire_date,
  c.contract_no,
  c.contract_type,
  c.start_date,
  c.end_date,
  c.sign_date,
  c.status,
  c.renew_from_id
FROM ehr_emp_contract c
INNER JOIN ehr_employee e ON e.id = c.employee_id
WHERE c.deleted = 0
  AND c.remark = 'AUTO_3Y_3Y_UNFIXED_20261116'
ORDER BY e.emp_no, c.start_date, c.id;

-- 如需撤销本脚本生成的数据，请先确认这些合同未被后续业务使用，再单独执行：
-- DELETE FROM ehr_emp_contract
-- WHERE remark = 'AUTO_3Y_3Y_UNFIXED_20261116';
