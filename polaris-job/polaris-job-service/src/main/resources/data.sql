-- ------------------------------------------------------------
-- 存量任务迁移种子（幂等：同 app_code + handler 已存在则跳过）
-- 由各应用原 @Scheduled 迁移而来，next_fire_time 留空，启动时由 JobStartupBootstrap 重算。
-- ------------------------------------------------------------

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000001, 'ehr', '年假每日刷新', 'annualLeaveRefresh', '每日 02:30 刷新员工年假额度', 'CRON', '0 30 2 * * ?', NULL, 1800, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'annualLeaveRefresh' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000002, 'ehr', '司龄与累计工龄刷新', 'seniorityRefresh', '每日 02:00 重算司龄与累计工龄', 'CRON', '0 0 2 * * ?', NULL, 3600, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'seniorityRefresh' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000003, 'ehr', '试用期与合同到期提醒', 'reminder', '每日 09:00 生成试用期/合同到期提醒', 'CRON', '0 0 9 * * ?', NULL, 1800, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'reminder' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000004, 'ehr', '年假结转到期提醒', 'annualLeaveCarryoverNotice', '每年 12 月 09:30 提醒年假结转即将到期', 'CRON', '0 30 9 * 12 ?', NULL, 1800, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'annualLeaveCarryoverNotice' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000005, 'ehr', '模拟考勤生成', 'attendanceMock', '每日 02:00 生成昨日模拟考勤（受配置埋点开关控制）', 'CRON', '0 0 2 * * ?', NULL, 1800, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'attendanceMock' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000012, 'ehr', '日考勤结果兜底重算', 'attendanceDaily', '每日 03:00 重算昨日全员日结果+月度汇总（独立于模拟考勤，不受 mock 开关控制）', 'CRON', '0 0 3 * * ?', NULL, 1800, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'attendanceDaily' AND deleted = 0);

-- 月度考勤汇总兜底：排在 attendanceDaily 03:00 之后的 04:00，全量重算上一个完整日历月的全员汇总
-- 兜住月底之后才审批通过的请假/补卡/加班等变更，已确认锁定（confirmed=1）的月份自动跳过
INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000020, 'ehr', '月度考勤汇总兜底', 'attendanceMonthly', '每日 04:00 全量重算上一个完整日历月的全员月度汇总，兜住月底后才审批通过的请假/补卡/加班', 'CRON', '0 0 4 * * ?', NULL, 1800, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'attendanceMonthly' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000006, 'ehr', '次年日历维护提醒', 'calendarMaintainRemind', '每日 09:30 检查次年考勤日历是否需要维护', 'CRON', '0 30 9 * * ?', NULL, 900, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'ehr' AND handler = 'calendarMaintainRemind' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000007, 'settings', '审批 SLA 扫描', 'approvalSla', '每小时第 5 分扫描超时审批', 'CRON', '0 5 * * * ?', NULL, 600, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'settings' AND handler = 'approvalSla' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000008, 'settings', '审批回调重试', 'approvalCallbackRetry', '每 60 秒重试失败的终审回调', 'FIXED_DELAY', NULL, 60, 600, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'settings' AND handler = 'approvalCallbackRetry' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000009, 'cms', '定时发布扫描', 'scheduledPublish', '每 30 秒扫描到点的待发布文章', 'FIXED_DELAY', NULL, 30, 300, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'cms' AND handler = 'scheduledPublish' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000010, 'blog', '阅读数落库', 'viewCountFlush', '每 5 分钟把阅读数批量写库', 'FIXED_DELAY', NULL, 300, 300, 0, 30, 'ROUND', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'blog' AND handler = 'viewCountFlush' AND deleted = 0);

INSERT INTO job_info (id, app_code, name, handler, description, trigger_type, cron_expr, interval_sec, timeout_sec, retry_times, retry_interval_sec, route_strategy, block_strategy, params, status, next_fire_time, lock_version)
SELECT 910000000000000011, 'gateway', '吊销名单同步', 'revocationSync', '每 5 秒从 sso-auth 同步已登出的 jti（多实例广播）', 'FIXED_DELAY', NULL, 5, 30, 0, 30, 'BROADCAST', 'DISCARD', '{}', 1, NULL, 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM job_info WHERE app_code = 'gateway' AND handler = 'revocationSync' AND deleted = 0);
