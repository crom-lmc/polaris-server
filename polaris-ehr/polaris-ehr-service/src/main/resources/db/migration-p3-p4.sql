-- ============================================================================
-- Polaris EHR P3/P4 存量库补字段脚本（MySQL 5.7）
-- 适用：已存在旧版 ehr 库、需要升级到 P3（招聘/门户投递）+ P4（考勤/年假）
-- 用法：mysql -uroot -p --default-character-set=utf8mb4 ehr < migration-p3-p4.sql
-- 注意：
--   1. 本脚本只补「已存在表」的新增列/索引并回填数据，可对旧库执行一次；
--   2. P3/P4 新增的 13 张表由 schema.sql 的 CREATE TABLE IF NOT EXISTS 创建，
--      执行完本脚本后再执行一次 schema.sql 即可；
--   3. 若目标库已执行过本脚本，重复执行会因列已存在而报错，请勿重复跑。
-- ============================================================================
SET NAMES utf8mb4;

-- 1. 员工档案：招聘来源、司龄与首次参加工作日期
ALTER TABLE `ehr_employee`
  ADD COLUMN `source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源 MANUAL/RECRUIT/PORTAL/IMPORT' AFTER `expected_regularize_date`,
  ADD COLUMN `source_offer_id` bigint(20) DEFAULT NULL COMMENT '来源 Offer ID（招聘转入时写入）' AFTER `source`,
  ADD COLUMN `seniority_start_date` date DEFAULT NULL COMMENT '司龄起算日（默认入职日，HR 按核定结果维护）' AFTER `source_offer_id`,
  ADD COLUMN `seniority_years` decimal(4,1) NOT NULL DEFAULT '0.0' COMMENT '当前司龄（整年，每日任务刷新）' AFTER `seniority_start_date`,
  ADD COLUMN `first_work_date` date DEFAULT NULL COMMENT '首次参加工作时间（HR 核定，累计工龄用于法定年假档位）' AFTER `seniority_years`,
  ADD COLUMN `seniority_confirmed_at` datetime DEFAULT NULL COMMENT '最近一次司龄确认时间' AFTER `first_work_date`,
  ADD COLUMN `seniority_confirmed_by` bigint(20) DEFAULT NULL COMMENT '最近一次司龄确认人' AFTER `seniority_confirmed_at`,
  ADD UNIQUE KEY `uk_employee_source_offer` (`source_offer_id`),
  ADD KEY `idx_emp_source` (`source`);

-- 存量员工按入职日回填司龄起算日与首次参加工作日期（前司工龄后续由 HR 核定）
UPDATE `ehr_employee`
SET `seniority_start_date` = `hire_date`,
    `first_work_date` = COALESCE(`first_work_date`, `hire_date`)
WHERE `seniority_start_date` IS NULL AND `hire_date` IS NOT NULL;

-- 2. 假期额度：按周期（自然年）生成 + 结转字段
ALTER TABLE `ehr_leave_quota`
  ADD COLUMN `period_start` date NULL COMMENT '额度周期起始日（自然年为当年1月1日）' AFTER `year`,
  ADD COLUMN `period_end` date NULL COMMENT '额度周期截止日（自然年为当年12月31日）' AFTER `period_start`,
  ADD COLUMN `period_no` int(11) NOT NULL DEFAULT '1' COMMENT '周期序号，续期递增' AFTER `period_end`,
  ADD COLUMN `carryover_in` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '上期结转进来' AFTER `frozen_days`,
  ADD COLUMN `carryover_out` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '结转到下期' AFTER `carryover_in`,
  ADD COLUMN `carryover_expire_at` date DEFAULT NULL COMMENT '结转额度失效日' AFTER `carryover_out`,
  ADD COLUMN `source` varchar(16) NOT NULL DEFAULT 'MANUAL' COMMENT '来源 AUTO自然年生成/MANUAL手工/CARRYOVER结转' AFTER `carryover_expire_at`,
  ADD COLUMN `calc_detail` varchar(500) DEFAULT '' COMMENT '生成依据快照（工龄/档位/折算）' AFTER `source`;

-- 存量额度按 year 回填周期
UPDATE `ehr_leave_quota`
SET `period_start` = MAKEDATE(`year`, 1),
    `period_end` = DATE(CONCAT(`year`, '-12-31')),
    `period_no` = 1
WHERE `period_start` IS NULL;

ALTER TABLE `ehr_leave_quota`
  MODIFY COLUMN `period_start` date NOT NULL COMMENT '额度周期起始日（自然年为当年1月1日）',
  MODIFY COLUMN `period_end` date NOT NULL COMMENT '额度周期截止日（自然年为当年12月31日）',
  ADD UNIQUE KEY `uk_quota_emp_type_period` (`employee_id`,`leave_type`,`period_start`);

-- 3. 请假类型规则：证明材料配置
ALTER TABLE `ehr_leave_type_rule`
  ADD COLUMN `proof_types` varchar(128) NOT NULL DEFAULT '' COMMENT '允许的证明文件类型，如 pdf,jpg,png；空=不限' AFTER `attachment_required`,
  ADD COLUMN `proof_min_hours` decimal(4,1) NOT NULL DEFAULT '0.0' COMMENT '达到多少小时才强制证明（0=所有时长都要）' AFTER `proof_types`,
  ADD COLUMN `proof_hint` varchar(255) NOT NULL DEFAULT '' COMMENT '证明上传提示（员工手册口径）' AFTER `proof_min_hours`;

-- 4. 加班：调休结算字段
ALTER TABLE `ehr_overtime`
  ADD COLUMN `settle_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '调休结算：0未结算/1已结算' AFTER `status`,
  ADD COLUMN `effective_hours` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '审批通过后计入调休的有效小时数' AFTER `settle_status`,
  ADD COLUMN `settled_at` datetime DEFAULT NULL COMMENT '结算为调休额度的时间' AFTER `effective_hours`,
  ADD COLUMN `calc_detail` varchar(500) DEFAULT '' COMMENT '核算快照：规则/日类型/比例/扣减' AFTER `settled_at`;

-- 5. 月度考勤：迟到/早退/缺勤/实际工时 + 月度解锁留痕
ALTER TABLE `ehr_attendance_month`
  ADD COLUMN `late_minutes` int(11) NOT NULL DEFAULT '0' COMMENT '迟到分钟数合计' AFTER `absent_days`,
  ADD COLUMN `early_minutes` int(11) NOT NULL DEFAULT '0' COMMENT '早退分钟数合计' AFTER `late_minutes`,
  ADD COLUMN `absent_hours` decimal(6,2) NOT NULL DEFAULT '0.00' COMMENT '缺勤小时数（按实际分钟/60，不向上取整）' AFTER `early_minutes`,
  ADD COLUMN `work_hours` decimal(7,2) NOT NULL DEFAULT '0.00' COMMENT '实际出勤小时数' AFTER `absent_hours`,
  ADD COLUMN `unlock_reason` varchar(255) DEFAULT '' COMMENT '最近一次解锁原因' AFTER `confirmed`,
  ADD COLUMN `unlocked_at` datetime DEFAULT NULL COMMENT '最近一次解锁时间' AFTER `unlock_reason`,
  ADD COLUMN `unlocked_by` bigint(20) DEFAULT NULL COMMENT '最近一次解锁操作人统一账号ID' AFTER `unlocked_at`;

-- 6. P3/P4 补漏：附件表与额度流水表缺少 updated_at（实体继承 BaseEntity，insert 会带该列，缺失即报 Unknown column）
ALTER TABLE `ehr_attachment`
  ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`;

ALTER TABLE `ehr_leave_quota_log`
  ADD COLUMN `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`;

