-- EHR 多页签权限重建：页面菜单 -> 页签/数据视图 -> 操作按钮
-- 执行前请切换到统一账号中心数据库。本脚本只处理 app_code='ehr'，不会修改 SSO/settings 的同名权限。
-- 首次执行会备份目标权限及其全部角色关联；重复执行保留首次备份，并按下方矩阵重新授权。

DROP TEMPORARY TABLE IF EXISTS tmp_ehr_tab_permission_codes;
CREATE TEMPORARY TABLE tmp_ehr_tab_permission_codes (
  code varchar(128) NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO tmp_ehr_tab_permission_codes (code) VALUES
('leave'),('leave:view'),('leave:apply'),('leave:approve'),('leave:view:team'),
('leave:mine'),('leave:all'),('leave:team'),
('leave:mine:apply'),('leave:mine:cancel'),('leave:mine:early-return'),('leave:mine:waive-carryover'),
('recruit'),('recruit:view'),('recruit:manage'),
('recruit:board'),('recruit:offers'),('recruit:jobs'),('recruit:interviews'),
('recruit:board:candidate:create'),('recruit:board:candidate:edit'),
('recruit:board:candidate:eliminate'),('recruit:board:candidate:restore'),
('recruit:board:interview:schedule'),('recruit:board:interview:cancel'),
('recruit:board:communication:add'),('recruit:board:offer:create'),('recruit:board:resume:download'),
('recruit:offers:create'),('recruit:offers:cancel'),('recruit:offers:confirm'),
('recruit:offers:decline'),('recruit:offers:onboard'),
('recruit:jobs:create'),('recruit:jobs:update'),('recruit:jobs:publish'),
('recruit:jobs:close'),('recruit:jobs:delete'),('recruit:interviews:feedback'),
('attendance'),('overtime:view'),('overtime:approve'),
('attendance:view'),('attendance:manage'),('attendance:month:unlock'),
('attendance:overtime'),('attendance:overtime:mine'),('attendance:overtime:team'),('attendance:overtime:all'),
('attendance:monthly'),('attendance:my'),('attendance:daily'),('attendance:punches'),('attendance:corrections'),
('attendance:rules'),('attendance:calendar'),('attendance:annual'),
('attendance:overtime:preview'),('attendance:overtime:submit'),('attendance:overtime:cancel'),
('attendance:monthly:confirm'),('attendance:monthly:unlock'),('attendance:monthly:export'),
('attendance:overtime:month:confirm'),('attendance:overtime:month:unlock'),('attendance:overtime:month:export'),
('attendance:my:correction:apply'),('attendance:my:correction:cancel'),
('attendance:daily:generate'),('attendance:daily:mock'),('attendance:punches:create'),
('attendance:corrections:approve'),('attendance:corrections:reject'),
('attendance:rules:create'),('attendance:rules:update'),('attendance:rules:delete'),('attendance:rules:toggle'),
('attendance:calendar:save'),('attendance:calendar:import'),('attendance:calendar:apply-next-year'),
('attendance:annual:create'),('attendance:annual:update'),('attendance:annual:enable'),
('attendance:annual:generate'),('attendance:annual:recalculate'),
('approval'),('approval:view'),('approval:approve'),('approval:done'),
('approval:todo'),('approval:approved'),('approval:started'),('approval:cc'),('approval:delegated'),
('approval:handover'),('approval:resign'),
('approval:todo:approve'),('approval:todo:reject'),('approval:todo:transfer'),('approval:todo:add-sign'),
('approval:started:withdraw'),('approval:handover:confirm'),
('approval:resign:apply'),('approval:resign:cancel'),('approval:resign:view-all');

-- 备份包含逻辑删除记录，避免唯一键 uk_perm_app_code(app_code, code) 影响重建。
CREATE TABLE IF NOT EXISTS backup_ehr_tab_permission_20261115 AS
SELECT p.*
FROM sso_permission p
INNER JOIN tmp_ehr_tab_permission_codes t ON t.code = p.code
WHERE p.app_code = 'ehr';

CREATE TABLE IF NOT EXISTS backup_ehr_tab_role_permission_20261115 AS
SELECT rp.*
FROM sso_role_permission rp
INNER JOIN sso_permission p ON p.id = rp.permission_id
INNER JOIN tmp_ehr_tab_permission_codes t ON t.code = p.code
WHERE p.app_code = 'ehr';

-- 单独备份非 HR 角色的组织/人员写权限，首次备份保留，重复执行不覆盖。
CREATE TABLE IF NOT EXISTS backup_ehr_non_hr_org_employee_write_20261115 AS
SELECT rp.*
FROM sso_role_permission rp
INNER JOIN sso_permission p ON p.id = rp.permission_id
INNER JOIN sso_role r ON r.id = rp.role_id
WHERE p.app_code = 'ehr'
  AND p.code IN (
    'org:manage','employee:create','employee:edit','employee:delete','employee:account',
    'employee:resign','employee:import','employee:export','contract:manage'
  )
  AND r.code NOT IN ('SUPER_ADMIN','HR_SPECIALIST','HR_RECRUITER','HR_COMP_BENEFIT','HR_ER');

START TRANSACTION;

-- 非 HR 角色仅保留组织/人员查看，清除历史或手工授予的写权限。
DELETE rp
FROM sso_role_permission rp
INNER JOIN sso_permission p ON p.id = rp.permission_id
INNER JOIN sso_role r ON r.id = rp.role_id
WHERE p.app_code = 'ehr'
  AND p.code IN (
    'org:manage','employee:create','employee:edit','employee:delete','employee:account',
    'employee:resign','employee:import','employee:export','contract:manage'
  )
  AND r.code NOT IN ('SUPER_ADMIN','HR_SPECIALIST','HR_RECRUITER','HR_COMP_BENEFIT','HR_ER');

-- 清理所有角色上的旧授权，包括非内置角色；不从旧关联复制权限。
DELETE rp
FROM sso_role_permission rp
INNER JOIN sso_permission p ON p.id = rp.permission_id
INNER JOIN tmp_ehr_tab_permission_codes t ON t.code = p.code
WHERE p.app_code = 'ehr';

-- 按按钮、子菜单、根菜单的顺序物理删除，包含逻辑删除记录。
DELETE p
FROM sso_permission p
INNER JOIN tmp_ehr_tab_permission_codes t ON t.code = p.code
WHERE p.app_code = 'ehr' AND p.type = 2;

DELETE p
FROM sso_permission p
INNER JOIN tmp_ehr_tab_permission_codes t ON t.code = p.code
WHERE p.app_code = 'ehr' AND p.type = 1 AND p.parent_id <> 0;

DELETE p
FROM sso_permission p
INNER JOIN tmp_ehr_tab_permission_codes t ON t.code = p.code
WHERE p.app_code = 'ehr' AND p.type = 1 AND p.parent_id = 0;

INSERT INTO sso_permission
(id, code, name, type, parent_id, sort, remark, created_at, updated_at, deleted, app_code) VALUES
(375175900486045696,'leave','请假管理',1,0,3,'',NOW(),NOW(),0,'ehr'),
(375182598000000020,'recruit','招聘管理',1,0,4,'',NOW(),NOW(),0,'ehr'),
(375194196740542464,'approval','审批中心',1,0,6,'',NOW(),NOW(),0,'ehr'),
(375182598000000017,'attendance','考勤管理',1,0,7,'',NOW(),NOW(),0,'ehr'),
-- 请假：页签与我的请假按钮
(2026111500000001001,'leave:mine','我的请假',1,375175900486045696,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001002,'leave:all','审批列表',1,375175900486045696,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001003,'leave:team','团队请假',1,375175900486045696,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001011,'leave:mine:apply','提交请假',2,2026111500000001001,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001012,'leave:mine:cancel','撤销请假',2,2026111500000001001,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001013,'leave:mine:early-return','提前返岗',2,2026111500000001001,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001014,'leave:mine:waive-carryover','放弃结转',2,2026111500000001001,4,'',NOW(),NOW(),0,'ehr'),
-- 招聘：四个页签
(2026111500000001101,'recruit:board','候选人看板',1,375182598000000020,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001102,'recruit:offers','Offer 列表',1,375182598000000020,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001103,'recruit:jobs','职位管理',1,375182598000000020,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001104,'recruit:interviews','我的面试',1,375182598000000020,4,'',NOW(),NOW(),0,'ehr'),
(2026111500000001111,'recruit:board:candidate:create','新增候选人',2,2026111500000001101,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001112,'recruit:board:candidate:edit','编辑候选人',2,2026111500000001101,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001113,'recruit:board:candidate:eliminate','淘汰候选人',2,2026111500000001101,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001114,'recruit:board:candidate:restore','恢复候选人',2,2026111500000001101,4,'',NOW(),NOW(),0,'ehr'),
(2026111500000001115,'recruit:board:interview:schedule','安排面试',2,2026111500000001101,5,'',NOW(),NOW(),0,'ehr'),
(2026111500000001116,'recruit:board:interview:cancel','取消面试',2,2026111500000001101,6,'',NOW(),NOW(),0,'ehr'),
(2026111500000001117,'recruit:board:communication:add','新增沟通记录',2,2026111500000001101,7,'',NOW(),NOW(),0,'ehr'),
(2026111500000001118,'recruit:board:offer:create','看板发起 Offer',2,2026111500000001101,8,'',NOW(),NOW(),0,'ehr'),
(2026111500000001119,'recruit:board:resume:download','下载简历',2,2026111500000001101,9,'',NOW(),NOW(),0,'ehr'),
(2026111500000001121,'recruit:offers:create','发起 Offer',2,2026111500000001102,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001122,'recruit:offers:cancel','撤销 Offer',2,2026111500000001102,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001123,'recruit:offers:confirm','确认入职',2,2026111500000001102,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001124,'recruit:offers:decline','拒绝 Offer',2,2026111500000001102,4,'',NOW(),NOW(),0,'ehr'),
(2026111500000001125,'recruit:offers:onboard','办理入职',2,2026111500000001102,5,'',NOW(),NOW(),0,'ehr'),
(2026111500000001131,'recruit:jobs:create','新增职位',2,2026111500000001103,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001132,'recruit:jobs:update','编辑职位',2,2026111500000001103,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001133,'recruit:jobs:publish','发布职位',2,2026111500000001103,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001134,'recruit:jobs:close','关闭职位',2,2026111500000001103,4,'',NOW(),NOW(),0,'ehr'),
(2026111500000001135,'recruit:jobs:delete','删除职位',2,2026111500000001103,5,'',NOW(),NOW(),0,'ehr'),
(2026111500000001141,'recruit:interviews:feedback','提交面试反馈',2,2026111500000001104,1,'',NOW(),NOW(),0,'ehr'),
-- 考勤：九个页签及加班数据视图
(2026111500000001201,'attendance:overtime','加班',1,375182598000000017,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001209,'attendance:monthly','员工考勤汇总',1,375182598000000017,2,'仅 HR；仍受组织数据范围限制',NOW(),NOW(),0,'ehr'),
(2026111500000001202,'attendance:my','我的考勤',1,375182598000000017,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001203,'attendance:daily','日结果',1,375182598000000017,4,'仅 HR；仍受组织数据范围限制',NOW(),NOW(),0,'ehr'),
(2026111500000001204,'attendance:punches','打卡记录',1,375182598000000017,5,'',NOW(),NOW(),0,'ehr'),
(2026111500000001205,'attendance:corrections','补卡审批',1,375182598000000017,6,'',NOW(),NOW(),0,'ehr'),
(2026111500000001206,'attendance:rules','考勤规则',1,375182598000000017,7,'仅 HR',NOW(),NOW(),0,'ehr'),
(2026111500000001207,'attendance:calendar','工作日历',1,375182598000000017,8,'仅 HR',NOW(),NOW(),0,'ehr'),
(2026111500000001208,'attendance:annual','年假策略',1,375182598000000017,9,'仅 HR',NOW(),NOW(),0,'ehr'),
(2026111500000001211,'attendance:overtime:mine','我的加班',1,2026111500000001201,1,'数据视图',NOW(),NOW(),0,'ehr'),
(2026111500000001212,'attendance:overtime:team','团队加班',1,2026111500000001201,2,'部门负责人及相关 HR；仍受组织数据范围限制',NOW(),NOW(),0,'ehr'),
(2026111500000001213,'attendance:overtime:all','全部加班',1,2026111500000001201,3,'相关 HR；仍受组织数据范围限制',NOW(),NOW(),0,'ehr'),
(2026111500000001221,'attendance:overtime:preview','加班试算',2,2026111500000001201,11,'',NOW(),NOW(),0,'ehr'),
(2026111500000001222,'attendance:overtime:submit','提交加班',2,2026111500000001201,12,'',NOW(),NOW(),0,'ehr'),
(2026111500000001223,'attendance:overtime:cancel','撤销加班',2,2026111500000001201,13,'',NOW(),NOW(),0,'ehr'),
(2026111500000001224,'attendance:monthly:confirm','确认月度考勤',2,2026111500000001209,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001225,'attendance:monthly:unlock','解锁月度考勤',2,2026111500000001209,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001226,'attendance:monthly:export','导出月度考勤',2,2026111500000001209,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001231,'attendance:my:correction:apply','申请补卡',2,2026111500000001202,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001232,'attendance:my:correction:cancel','撤销补卡',2,2026111500000001202,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001241,'attendance:daily:generate','生成日结果',2,2026111500000001203,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001242,'attendance:daily:mock','生成模拟数据',2,2026111500000001203,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001251,'attendance:punches:create','补录打卡',2,2026111500000001204,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001261,'attendance:corrections:approve','补卡通过',2,2026111500000001205,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001262,'attendance:corrections:reject','补卡驳回',2,2026111500000001205,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001271,'attendance:rules:create','新增考勤规则',2,2026111500000001206,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001272,'attendance:rules:update','编辑考勤规则',2,2026111500000001206,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001273,'attendance:rules:delete','删除考勤规则',2,2026111500000001206,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001274,'attendance:rules:toggle','启停考勤规则',2,2026111500000001206,4,'',NOW(),NOW(),0,'ehr'),
(2026111500000001281,'attendance:calendar:save','保存工作日历',2,2026111500000001207,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001282,'attendance:calendar:import','导入工作日历',2,2026111500000001207,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001283,'attendance:calendar:apply-next-year','应用到下一年度',2,2026111500000001207,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001291,'attendance:annual:create','新增年假策略',2,2026111500000001208,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001292,'attendance:annual:update','编辑年假策略',2,2026111500000001208,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001293,'attendance:annual:enable','启停年假策略',2,2026111500000001208,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001294,'attendance:annual:generate','生成年度额度',2,2026111500000001208,4,'',NOW(),NOW(),0,'ehr'),
(2026111500000001295,'attendance:annual:recalculate','重算年度额度',2,2026111500000001208,5,'',NOW(),NOW(),0,'ehr'),
-- 审批中心：七个页签及操作按钮
(2026111500000001301,'approval:todo','待我审批',1,375194196740542464,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001302,'approval:approved','已审批',1,375194196740542464,2,'',NOW(),NOW(),0,'ehr'),
(2026111500000001303,'approval:started','我发起的',1,375194196740542464,3,'',NOW(),NOW(),0,'ehr'),
(2026111500000001304,'approval:cc','抄送我',1,375194196740542464,4,'',NOW(),NOW(),0,'ehr'),
(2026111500000001305,'approval:delegated','我委托的',1,375194196740542464,5,'',NOW(),NOW(),0,'ehr'),
(2026111500000001306,'approval:handover','待我交接',1,375194196740542464,6,'',NOW(),NOW(),0,'ehr'),
(2026111500000001307,'approval:resign','离职/减员申请',1,375194196740542464,7,'',NOW(),NOW(),0,'ehr'),
(2026111500000001311,'approval:todo:approve','审批通过',2,2026111500000001301,1,'仍需本人待办任务',NOW(),NOW(),0,'ehr'),
(2026111500000001312,'approval:todo:reject','审批驳回',2,2026111500000001301,2,'仍需本人待办任务',NOW(),NOW(),0,'ehr'),
(2026111500000001313,'approval:todo:transfer','审批转办',2,2026111500000001301,3,'仍需本人待办任务',NOW(),NOW(),0,'ehr'),
(2026111500000001314,'approval:todo:add-sign','审批加签',2,2026111500000001301,4,'仍需本人待办任务',NOW(),NOW(),0,'ehr'),
(2026111500000001321,'approval:started:withdraw','撤回申请',2,2026111500000001303,1,'仍需本人发起',NOW(),NOW(),0,'ehr'),
(2026111500000001331,'approval:handover:confirm','确认交接',2,2026111500000001306,1,'仍需指定交接人',NOW(),NOW(),0,'ehr'),
(2026111500000001341,'approval:resign:apply','提交离职申请',2,2026111500000001307,1,'',NOW(),NOW(),0,'ehr'),
(2026111500000001342,'approval:resign:cancel','撤销离职申请',2,2026111500000001307,2,'仍需本人申请',NOW(),NOW(),0,'ehr'),
(2026111500000001343,'approval:resign:view-all','查看全部离职申请',2,2026111500000001307,3,'仍受组织数据范围限制',NOW(),NOW(),0,'ehr');

DROP TEMPORARY TABLE IF EXISTS tmp_ehr_tab_role_grants;
CREATE TEMPORARY TABLE tmp_ehr_tab_role_grants (
  role_id bigint NOT NULL,
  permission_code varchar(128) NOT NULL,
  PRIMARY KEY (role_id, permission_code)
) ENGINE=MEMORY;

-- 六个业务角色共同的组织/人员只读及自助权限；写权限按下方角色单独授予。
INSERT IGNORE INTO tmp_ehr_tab_role_grants (role_id, permission_code)
SELECT r.id, p.code
FROM sso_role r
INNER JOIN sso_permission p ON p.app_code = 'ehr' AND p.deleted = 0 AND p.code IN (
  'org','org:view','employee','employee:view',
  'leave','leave:mine','leave:mine:apply','leave:mine:cancel','leave:mine:early-return','leave:mine:waive-carryover',
  'attendance','attendance:overtime','attendance:overtime:mine',
  'attendance:overtime:preview','attendance:overtime:submit','attendance:overtime:cancel',
  'attendance:my','attendance:my:correction:apply','attendance:my:correction:cancel',
  'recruit','recruit:interviews','recruit:interviews:feedback',
  'approval','approval:todo','approval:approved','approval:started','approval:cc','approval:delegated',
  'approval:started:withdraw','approval:resign','approval:resign:apply','approval:resign:cancel'
)
WHERE r.id IN (2116193763556519941,2116193763556519942,2116193763556519943,
               2116193763556519944,2116193763556519945,2116193763556519946)
  AND r.deleted = 0;

-- 部门负责人：团队视图和审批处理。
INSERT IGNORE INTO tmp_ehr_tab_role_grants (role_id, permission_code)
SELECT 2116193763556519946, p.code
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.deleted = 0 AND p.code IN (
  'leave:team','attendance:overtime:team',
  'approval:todo:approve','approval:todo:reject','approval:todo:transfer','approval:todo:add-sign'
);

-- 招聘专员：候选人、Offer、职位全量管理；我的面试由公共自助权限提供。
INSERT IGNORE INTO tmp_ehr_tab_role_grants (role_id, permission_code)
SELECT 2116193763556519943, p.code
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.deleted = 0 AND p.code IN (
  'recruit:board','recruit:board:candidate:create','recruit:board:candidate:edit',
  'recruit:board:candidate:eliminate','recruit:board:candidate:restore',
  'recruit:board:interview:schedule','recruit:board:interview:cancel',
  'recruit:board:communication:add','recruit:board:offer:create','recruit:board:resume:download',
  'recruit:offers','recruit:offers:create','recruit:offers:cancel','recruit:offers:confirm',
  'recruit:offers:decline','recruit:offers:onboard',
  'recruit:jobs','recruit:jobs:create','recruit:jobs:update','recruit:jobs:publish',
  'recruit:jobs:close','recruit:jobs:delete'
);

-- 人事专员：请假和考勤全部页签、数据视图及按钮。
INSERT IGNORE INTO tmp_ehr_tab_role_grants (role_id, permission_code)
SELECT 2116193763556519942, p.code
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.deleted = 0 AND (
  p.id BETWEEN 2026111500000001001 AND 2026111500000001014
  OR p.id BETWEEN 2026111500000001201 AND 2026111500000001295
);

-- 薪酬绩效专员：全部加班、员工考勤汇总、日结果、打卡、规则、日历和年假策略。
INSERT IGNORE INTO tmp_ehr_tab_role_grants (role_id, permission_code)
SELECT 2116193763556519944, p.code
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.deleted = 0 AND p.code IN (
  'attendance:overtime:all',
  'attendance:monthly','attendance:monthly:confirm','attendance:monthly:unlock','attendance:monthly:export',
  'attendance:daily','attendance:daily:generate','attendance:daily:mock',
  'attendance:punches','attendance:punches:create',
  'attendance:rules','attendance:rules:create','attendance:rules:update',
  'attendance:rules:delete','attendance:rules:toggle',
  'attendance:calendar','attendance:calendar:save','attendance:calendar:import','attendance:calendar:apply-next-year',
  'attendance:annual','attendance:annual:create','attendance:annual:update',
  'attendance:annual:enable','attendance:annual:generate','attendance:annual:recalculate'
);

-- 员工关系专员：全部离职申请和交接确认。
INSERT IGNORE INTO tmp_ehr_tab_role_grants (role_id, permission_code)
SELECT 2116193763556519945, p.code
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.deleted = 0 AND p.code IN (
  'approval:handover','approval:handover:confirm','approval:resign:view-all'
);

-- 超级管理员：本次重建的全部菜单、页签、数据视图和按钮。
INSERT IGNORE INTO tmp_ehr_tab_role_grants (role_id, permission_code)
SELECT 2116193763556519940, p.code
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.deleted = 0 AND (
  p.id IN (375175900486045696,375182598000000017,375182598000000020,375194196740542464)
  OR p.id BETWEEN 2026111500000001001 AND 2026111500000001343
);

INSERT IGNORE INTO sso_role_permission (role_id, permission_id)
SELECT g.role_id, p.id
FROM tmp_ehr_tab_role_grants g
INNER JOIN sso_role r ON r.id = g.role_id AND r.deleted = 0
INNER JOIN sso_permission p ON p.app_code = 'ehr' AND p.deleted = 0 AND p.code = g.permission_code;

COMMIT;

-- 核验 1：新权限树，正常应为 88 条（4 个根节点 + 84 个新节点）。
SELECT p.id, p.code, p.name, p.type, p.parent_id, p.sort
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.deleted = 0 AND (
  p.id IN (375175900486045696,375182598000000017,375182598000000020,375194196740542464)
  OR p.id BETWEEN 2026111500000001001 AND 2026111500000001343
)
ORDER BY p.parent_id, p.sort, p.id;

-- 核验 2：重复权限码，应返回 0 行。
SELECT p.app_code, p.code, COUNT(*) AS cnt
FROM sso_permission p
WHERE p.app_code = 'ehr'
GROUP BY p.app_code, p.code
HAVING COUNT(*) > 1;

-- 核验 3：新树孤儿节点，应返回 0 行。
SELECT c.id, c.code, c.parent_id
FROM sso_permission c
LEFT JOIN sso_permission parent ON parent.id = c.parent_id AND parent.deleted = 0
WHERE c.app_code = 'ehr' AND c.deleted = 0
  AND c.id BETWEEN 2026111500000001001 AND 2026111500000001343
  AND (parent.id IS NULL OR parent.app_code <> 'ehr');

-- 核验 4：跨应用父子节点，应返回 0 行。
SELECT c.id, c.code, c.app_code, parent.id AS parent_id, parent.app_code AS parent_app_code
FROM sso_permission c
INNER JOIN sso_permission parent ON parent.id = c.parent_id
WHERE c.app_code = 'ehr' AND c.deleted = 0 AND parent.app_code <> c.app_code;

-- 核验 5：角色矩阵缺失关联，应返回 0 行。
SELECT r.code AS role_code, g.permission_code
FROM tmp_ehr_tab_role_grants g
INNER JOIN sso_role r ON r.id = g.role_id
LEFT JOIN sso_permission p ON p.app_code = 'ehr' AND p.deleted = 0 AND p.code = g.permission_code
LEFT JOIN sso_role_permission rp ON rp.role_id = g.role_id AND rp.permission_id = p.id
WHERE rp.role_id IS NULL
ORDER BY r.code, g.permission_code;

-- 核验 6：已删除旧权限，应返回 0 行。
SELECT p.code, p.name
FROM sso_permission p
WHERE p.app_code = 'ehr' AND p.code IN (
  'leave:view','leave:apply','leave:approve','leave:view:team',
  'recruit:view','recruit:manage','overtime:view','overtime:approve',
  'attendance:view','attendance:manage','attendance:month:unlock',
  'attendance:overtime:month:confirm','attendance:overtime:month:unlock','attendance:overtime:month:export',
  'approval:view','approval:approve','approval:done'
);

-- 核验 7：非 HR 角色残留的组织/人员写权限，应返回 0 行。
SELECT r.code AS role_code, p.code AS permission_code
FROM sso_role_permission rp
INNER JOIN sso_permission p ON p.id = rp.permission_id
INNER JOIN sso_role r ON r.id = rp.role_id
WHERE p.app_code = 'ehr'
  AND p.code IN (
    'org:manage','employee:create','employee:edit','employee:delete','employee:account',
    'employee:resign','employee:import','employee:export','contract:manage'
  )
  AND r.code NOT IN ('SUPER_ADMIN','HR_SPECIALIST','HR_RECRUITER','HR_COMP_BENEFIT','HR_ER');

DROP TEMPORARY TABLE IF EXISTS tmp_ehr_tab_role_grants;
DROP TEMPORARY TABLE IF EXISTS tmp_ehr_tab_permission_codes;
