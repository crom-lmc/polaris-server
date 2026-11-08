-- ============================================================
-- EHR 库表结构（独立库 ehr，MySQL 5.7+，全部幂等可重跑）
-- 组织架构是一棵自引用树：parent_id + type 白名单决定「什么能挂在什么下面」
--   GROUP 集团 → COMPANY 子公司 / DEPARTMENT 部门 / PROJECT 项目
--   COMPANY 子公司 → DEPARTMENT 部门 / PROJECT 项目
--   DEPARTMENT 部门 → DEPARTMENT 部门（可再分层）/ PROJECT 项目
--   PROJECT 项目 → SUB_PROJECT 子项目
--   SUB_PROJECT 子项目 → 叶子
-- path 存祖先 id 链（"1/2/3"），用于祖先判定与整棵子树查询，避免递归 SQL
-- ============================================================

CREATE TABLE IF NOT EXISTS ehr_org_node (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    parent_id   BIGINT       DEFAULT NULL COMMENT '上级节点；集团为 NULL',
    type        VARCHAR(16)  NOT NULL COMMENT 'GROUP/COMPANY/DEPARTMENT/PROJECT/SUB_PROJECT',
    code        VARCHAR(64)  DEFAULT '' COMMENT '组织编码：新增时按层级自动生成（XC0001 / XC00010001 / XC000100010001 ...），创建后不可改',
    name        VARCHAR(128) NOT NULL COMMENT '节点名称',
    leader      VARCHAR(64)  DEFAULT '' COMMENT '负责人（展示名，可与下面的人关联字段并存）',
    leader_employee_id BIGINT DEFAULT NULL COMMENT '负责人（关联 ehr_employee.id，审批流据此找人）',
    phone       VARCHAR(32)  DEFAULT '' COMMENT '联系电话',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '同级排序，小的在前',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 2停用',
    depth       INT          NOT NULL DEFAULT 1 COMMENT '层级深度，集团=1',
    path        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '祖先ID链，如 1/2/3（含自身）',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_org_parent (parent_id),
    KEY idx_org_type (type),
    KEY idx_org_path (path),
    KEY idx_org_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织架构节点';

CREATE TABLE IF NOT EXISTS ehr_employee (
    id               BIGINT       NOT NULL COMMENT '雪花ID',
    emp_no           VARCHAR(32)  NOT NULL COMMENT '工号（唯一）',
    name             VARCHAR(64)  NOT NULL COMMENT '姓名',
    gender           TINYINT      NOT NULL DEFAULT 1 COMMENT '1男 2女 0未知',
    mobile           VARCHAR(32)  DEFAULT '' COMMENT '手机号',
    email            VARCHAR(128) DEFAULT '' COMMENT '邮箱（开户时优先生成登录名）',
    native_place     VARCHAR(128) DEFAULT '' COMMENT '籍贯（省市区+详细地址拼接展示值）',
    native_province  VARCHAR(32)  DEFAULT '' COMMENT '籍贯-省（身份证地址码自动带出）',
    native_city      VARCHAR(32)  DEFAULT '' COMMENT '籍贯-市（身份证地址码自动带出，禁改）',
    native_district  VARCHAR(32)  DEFAULT '' COMMENT '籍贯-区/县（身份证地址码自动带出，禁改）',
    native_detail    VARCHAR(128) DEFAULT '' COMMENT '籍贯-详细地址',
    id_card          VARCHAR(18)  DEFAULT '' COMMENT '18位身份证号（应用层校验码+查重）',
    birthday         DATE         DEFAULT NULL COMMENT '出生日期（年龄动态计算不落库）',
    nation           VARCHAR(16)  DEFAULT '' COMMENT '民族（字典 general.nation）',
    marital_status   VARCHAR(16)  DEFAULT '' COMMENT '婚姻状况（字典 ehr.marital_status）',
    political_status VARCHAR(16)  DEFAULT '' COMMENT '政治面貌（字典 ehr.political_status）',
    education        VARCHAR(16)  DEFAULT '' COMMENT '最高学历（字典 ehr.education）',
    emergency_contact VARCHAR(64) DEFAULT '' COMMENT '紧急联系人',
    emergency_phone  VARCHAR(32)  DEFAULT '' COMMENT '紧急联系电话',
    current_address  VARCHAR(255) DEFAULT '' COMMENT '现居住址',
    org_id           BIGINT       NOT NULL COMMENT '所属组织节点（任意层级均可挂人）',
    position         VARCHAR(64)  DEFAULT '' COMMENT '职位',
    hire_date        DATE         DEFAULT NULL COMMENT '入职日期',
    probation_months TINYINT      DEFAULT NULL COMMENT '试用期时长（月）：1-12；免试用期为 NULL',
    exempt_probation TINYINT      DEFAULT NULL COMMENT '是否免试用期：1 免试用期，入职审批通过后直接转正式（40）',
    expected_regularize_date DATE DEFAULT NULL COMMENT '预计转正时间 = 入职日期 + 试用期月数；免试用期/缺失为 NULL',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '10待入职 20入职审批中 25入职审批通过 30试用期 40在职 50离职中 60已离职',
    resign_date      DATE         DEFAULT NULL COMMENT '离职日期（status=2 时必填）',
    resign_reason    VARCHAR(255) DEFAULT '' COMMENT '离职原因',
    account_id       BIGINT       DEFAULT NULL COMMENT '统一账号中心账户ID',
    account_username VARCHAR(64)  DEFAULT '' COMMENT '登录名快照',
    remark           VARCHAR(255) DEFAULT '' COMMENT '备注',
    deleted          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_emp_no (emp_no),
    KEY idx_emp_org (org_id),
    KEY idx_emp_account (account_id),
    KEY idx_emp_status (status),
    KEY idx_emp_deleted (deleted),
    KEY idx_emp_id_card (id_card)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工档案';

CREATE TABLE IF NOT EXISTS ehr_emp_bank (
    id          BIGINT      NOT NULL COMMENT '雪花ID',
    employee_id BIGINT      NOT NULL COMMENT '员工档案ID',
    bank        VARCHAR(16) NOT NULL COMMENT '银行（字典 ehr.bank）',
    account_no  VARCHAR(32) NOT NULL COMMENT '卡号（8-32位数字）',
    holder_name VARCHAR(64) DEFAULT '' COMMENT '持卡人',
    branch      VARCHAR(128) DEFAULT '' COMMENT '开户行/支行',
    is_primary  TINYINT     NOT NULL DEFAULT 0 COMMENT '1=工资卡（每档案至多一张，应用层保证）',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    deleted     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_empbank_emp (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工银行卡';

CREATE TABLE IF NOT EXISTS ehr_emp_education (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id BIGINT       NOT NULL COMMENT '员工档案ID',
    school      VARCHAR(128) NOT NULL COMMENT '学校',
    major       VARCHAR(128) DEFAULT '' COMMENT '专业',
    degree      VARCHAR(16)  NOT NULL COMMENT '学历（字典 ehr.education）',
    start_date  DATE         DEFAULT NULL COMMENT '入学日期',
    end_date    DATE         DEFAULT NULL COMMENT '毕业日期（null=至今）',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_empedu_emp (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工教育背景';

CREATE TABLE IF NOT EXISTS ehr_emp_work (
    id           BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id  BIGINT       NOT NULL COMMENT '员工档案ID',
    company      VARCHAR(128) NOT NULL COMMENT '工作单位',
    position     VARCHAR(64)  DEFAULT '' COMMENT '职位',
    start_date   DATE         DEFAULT NULL COMMENT '开始日期',
    end_date     DATE         DEFAULT NULL COMMENT '结束日期（null=至今）',
    leave_reason VARCHAR(255) DEFAULT '' COMMENT '离职原因',
    remark       VARCHAR(255) DEFAULT '' COMMENT '备注',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_empwork_emp (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工工作经历';

CREATE TABLE IF NOT EXISTS ehr_emp_family (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id BIGINT       NOT NULL COMMENT '员工档案ID',
    relation    VARCHAR(16)  NOT NULL COMMENT '关系（字典 ehr.family_relation）',
    name        VARCHAR(64)  NOT NULL COMMENT '姓名',
    company     VARCHAR(128) DEFAULT '' COMMENT '工作单位',
    phone       VARCHAR(32)  DEFAULT '' COMMENT '联系电话',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_empfam_emp (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工家庭成员';

CREATE TABLE IF NOT EXISTS ehr_leave (
    id                  BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id         BIGINT       NOT NULL COMMENT '申请人（员工档案ID）',
    account_id          BIGINT       NOT NULL COMMENT '申请人账户ID（网关可信头）',
    applicant_name      VARCHAR(64)  NOT NULL COMMENT '申请人姓名快照',
    leave_type          VARCHAR(16)  NOT NULL COMMENT 'ANNUAL/SICK/PERSONAL/MARRIAGE/MATERNITY/OTHER',
    start_date          DATE         NOT NULL COMMENT '开始日期',
    end_date            DATE         NOT NULL COMMENT '结束日期',
    days                DECIMAL(5,1) NOT NULL COMMENT '请假天数',
    reason              VARCHAR(500) NOT NULL COMMENT '请假事由',
    delegate_account_id BIGINT       DEFAULT NULL COMMENT '审批转办人账户ID（部门负责人请假必填）',
    delegate_name       VARCHAR(64)  DEFAULT '' COMMENT '审批转办人姓名快照',
    status              TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回 3已撤销',
    approver_account_id BIGINT       DEFAULT NULL COMMENT '审批人账户ID',
    approver_name       VARCHAR(64)  DEFAULT '' COMMENT '审批人',
    approve_note        VARCHAR(255) DEFAULT '' COMMENT '审批意见',
    approved_at         DATETIME     DEFAULT NULL COMMENT '审批时间',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_leave_status (status),
    KEY idx_leave_account (account_id),
    KEY idx_leave_employee (employee_id),
    KEY idx_leave_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假申请';

-- 旧库补列：审批转办人（部门负责人请假必填，审批通过后按请假期间登记审批委托）
SET @lv_dlg_col = (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = 'ehr' AND table_name = 'ehr_leave' AND column_name = 'delegate_account_id');
SET @lv_dlg_sql = IF(@lv_dlg_col = 0,
    "ALTER TABLE ehr_leave ADD COLUMN delegate_account_id BIGINT DEFAULT NULL COMMENT '审批转办人账户ID' AFTER reason",
    'SELECT 1');
PREPARE lv_dlg_stmt FROM @lv_dlg_sql; EXECUTE lv_dlg_stmt; DEALLOCATE PREPARE lv_dlg_stmt;

SET @lv_dlgn_col = (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = 'ehr' AND table_name = 'ehr_leave' AND column_name = 'delegate_name');
SET @lv_dlgn_sql = IF(@lv_dlgn_col = 0,
    "ALTER TABLE ehr_leave ADD COLUMN delegate_name VARCHAR(64) DEFAULT '' COMMENT '审批转办人姓名快照' AFTER delegate_account_id",
    'SELECT 1');
PREPARE lv_dlgn_stmt FROM @lv_dlgn_sql; EXECUTE lv_dlgn_stmt; DEALLOCATE PREPARE lv_dlgn_stmt;

-- ------------------------------------------------------------
-- 离职申请 / 减员申请（统一审批流）
--   source=SELF  员工自己在 EHR 里提交离职申请
--   source=HR    HR 在人员列表上提交「减员」
-- 审批通过后才真正调用 EmployeeService.resign（置离职 + 停用账号），
-- 因此「列表上点一下人就没」变成了「提交 → 部门负责人审批 → 生效」。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ehr_resign_apply (
    id                   BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id          BIGINT       NOT NULL COMMENT '被办理人（员工档案ID）',
    emp_no               VARCHAR(32)  DEFAULT '' COMMENT '工号快照',
    apply_no             VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '申请单号（按配置中心单号规则生成）',
    employee_name        VARCHAR(64)  NOT NULL COMMENT '姓名快照',
    org_id               BIGINT       DEFAULT NULL COMMENT '所属组织ID快照',
    org_name             VARCHAR(128) DEFAULT '' COMMENT '所属组织名快照',
    source               VARCHAR(16)  NOT NULL COMMENT 'SELF 员工申请 / HR 减员',
    applicant_account_id BIGINT       NOT NULL COMMENT '提交人账户ID',
    applicant_name       VARCHAR(64)  DEFAULT '' COMMENT '提交人',
    resign_date          DATE         DEFAULT NULL COMMENT '拟离职日期',
    reason               VARCHAR(255) DEFAULT '' COMMENT '离职原因 / 减员原因',
    handover             VARCHAR(255) DEFAULT '' COMMENT '工作交接说明（备注，明细在 ehr_handover_item）',
    handover_employee_id BIGINT       DEFAULT NULL COMMENT '交接人员工ID',
    handover_account_id  BIGINT       DEFAULT NULL COMMENT '交接人账户ID（登录后确认交接项）',
    handover_name        VARCHAR(64)  DEFAULT '' COMMENT '交接人姓名快照',
    handover_status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0待交接 1交接已全部确认 2无需交接',
    handover_confirmed_at DATETIME    DEFAULT NULL COMMENT '交接完成时间',
    status               TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审批 1已通过 2已驳回 3已撤销',
    approver_account_id  BIGINT       DEFAULT NULL COMMENT '审批人账户ID（提交时按组织负责人快照）',
    approver_name        VARCHAR(64)  DEFAULT '' COMMENT '审批人',
    approver_org_name    VARCHAR(128) DEFAULT '' COMMENT '审批人所在节点（如「西安分公司」，用于说明为什么是上级审批）',
    approve_note         VARCHAR(255) DEFAULT '' COMMENT '审批意见',
    approved_at          DATETIME     DEFAULT NULL COMMENT '审批时间',
    hr_approver_account_id BIGINT     DEFAULT NULL COMMENT '第二级审批人：集团人力资源中心负责人账户ID',
    hr_approver_name     VARCHAR(64)  DEFAULT '' COMMENT '第二级审批人姓名快照',
    hr_approve_note      VARCHAR(500) DEFAULT '' COMMENT '第二级审批意见',
    hr_approved_at       DATETIME     DEFAULT NULL COMMENT '第二级审批时间',
    prev_status          TINYINT      DEFAULT NULL COMMENT '提交前员工状态（30试用/40在职），驳回或撤销时还原用',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_resign_status (status),
    KEY idx_resign_employee (employee_id),
    KEY idx_resign_approver (approver_account_id),
    KEY idx_resign_applicant (applicant_account_id),
    KEY idx_resign_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离职/减员申请（部门负责人审批）';

-- ------------------------------------------------------------
-- 交接项目清单：一张离职/减员单挂多条，交接人逐条确认
-- 全部确认（handover_status=1）之后才允许负责人审批 —— 这是「交接没完不能走」的落地点。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ehr_handover_item (
    id                    BIGINT       NOT NULL COMMENT '雪花ID',
    apply_id              BIGINT       NOT NULL COMMENT '所属离职/减员申请ID',
    employee_id           BIGINT       DEFAULT NULL COMMENT '所属员工ID（被交接的离职/减员员工）',
    seq                   INT          NOT NULL DEFAULT 1 COMMENT '排序号，按提交时的顺序',
    content               VARCHAR(255) NOT NULL COMMENT '交接内容（一项一条）',
    status                TINYINT      NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认',
    confirmer_account_id  BIGINT       DEFAULT NULL COMMENT '确认人账户ID（= 交接人）',
    confirmed_at          DATETIME     DEFAULT NULL COMMENT '确认时间',
    note                  VARCHAR(255) DEFAULT '' COMMENT '确认备注 / 遗留问题',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_handover_apply (apply_id),
    KEY idx_handover_emp (employee_id),
    KEY idx_handover_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='离职/减员的工作交接项目（交接人逐项确认）';

-- ------------------------------------------------------------
-- 复职申请（离职 / 减员后再入职）
-- 审批走 settings 的 ehr.rehire 模板；通过后由回调调用 EmployeeService.reinstate，
-- 本表只负责「谁在什么时候申请把谁请回来」和审批指针。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ehr_rehire_apply (
    id                    BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id           BIGINT       NOT NULL COMMENT '员工档案ID',
    emp_no                VARCHAR(32)  DEFAULT '' COMMENT '工号快照',
    employee_name         VARCHAR(64)  DEFAULT '' COMMENT '姓名快照',
    org_id                BIGINT       DEFAULT NULL COMMENT '提交时所在组织',
    org_name              VARCHAR(128) DEFAULT '' COMMENT '组织名快照',
    resign_date           DATE         DEFAULT NULL COMMENT '原离职日期',
    rehire_date           DATE         DEFAULT NULL COMMENT '拟复职日期',
    reason                VARCHAR(255) DEFAULT '' COMMENT '复职原因',
    enable_account        TINYINT      NOT NULL DEFAULT 1 COMMENT '复职时是否启用统一账号',
    status                TINYINT      NOT NULL DEFAULT 0 COMMENT '0审批中 1已通过 2已驳回 3已撤销',
    applicant_account_id  BIGINT       DEFAULT NULL COMMENT '提交人账号ID',
    applicant_name        VARCHAR(64)  DEFAULT '' COMMENT '提交人姓名',
    approval_instance_id  BIGINT       DEFAULT NULL COMMENT '审批中心实例ID',
    deleted               TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_rehire_emp (employee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='复职申请';

-- ------------------------------------------------------------
-- 转正申请（试用期 → 正式员工）
-- 审批走 settings 的 ehr.regularize 模板；通过后由回调把档案置正式在职(40)。
-- 单独建表的理由：一次试用期可能提交多次（驳回后改内容重提），
-- 员工档案只能存最新一次，历史申请单会丢；申请单有自己的状态机和审批指针。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ehr_regularize_apply (
    id                       BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id              BIGINT       NOT NULL COMMENT '员工档案ID',
    emp_no                   VARCHAR(32)  DEFAULT '' COMMENT '工号快照',
    apply_no                 VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '申请单号（按配置中心单号规则生成）',
    employee_name            VARCHAR(64)  DEFAULT '' COMMENT '姓名快照',
    org_id                   BIGINT       DEFAULT NULL COMMENT '提交时所在组织ID',
    org_name                 VARCHAR(128) DEFAULT '' COMMENT '组织名快照',
    org_path                 VARCHAR(255) DEFAULT '' COMMENT '组织全路径快照（集团 / 子公司 / 部门）',
    position                 VARCHAR(128) DEFAULT '' COMMENT '职位快照',
    hire_date                DATE         DEFAULT NULL COMMENT '入职日期快照',
    probation_months         TINYINT      DEFAULT NULL COMMENT '试用期月数快照（免试用为 0）',
    expected_regularize_date DATE         DEFAULT NULL COMMENT '预计转正日期快照',
    regularize_date          DATE         DEFAULT NULL COMMENT '申请转正日期',
    self_review              VARCHAR(500) DEFAULT '' COMMENT '试用期工作自评',
    work_summary             VARCHAR(1000) DEFAULT '' COMMENT '试用期工作业绩与成果',
    reason                   VARCHAR(500) DEFAULT '' COMMENT '转正申请理由',
    next_plan                VARCHAR(1000) DEFAULT '' COMMENT '转正后的工作目标与计划',
    status                   TINYINT      NOT NULL DEFAULT 0 COMMENT '0审批中 1已通过 2已驳回 3已撤销',
    applicant_account_id     BIGINT       DEFAULT NULL COMMENT '提交人账号ID',
    applicant_name           VARCHAR(64)  DEFAULT '' COMMENT '提交人姓名快照',
    approval_instance_id     BIGINT       DEFAULT NULL COMMENT '审批中心实例ID',
    deleted                  TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_regularize_emp (employee_id, status),
    KEY idx_regularize_status (status),
    KEY idx_regularize_applicant (applicant_account_id),
    KEY idx_regularize_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='转正申请';

-- 旧库补列：申请单号（按配置中心「单号规则」生成，列表与审批展示用）
SET @rsn_col = (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = 'ehr' AND table_name = 'ehr_resign_apply' AND column_name = 'apply_no');
SET @rsn_sql = IF(@rsn_col = 0,
    "ALTER TABLE ehr_resign_apply ADD COLUMN apply_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '申请单号' AFTER emp_no",
    'SELECT 1');
PREPARE rsn_stmt FROM @rsn_sql; EXECUTE rsn_stmt; DEALLOCATE PREPARE rsn_stmt;

SET @rgz_col = (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = 'ehr' AND table_name = 'ehr_regularize_apply' AND column_name = 'apply_no');
SET @rgz_sql = IF(@rgz_col = 0,
    "ALTER TABLE ehr_regularize_apply ADD COLUMN apply_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '申请单号' AFTER emp_no",
    'SELECT 1');
PREPARE rgz_stmt FROM @rgz_sql; EXECUTE rgz_stmt; DEALLOCATE PREPARE rgz_stmt;

-- ------------------------------------------------------------
-- 部门负责人变更记录（每一次「设置 / 更换 / 清除」负责人各留一行）
--   change_type=SET     首次设置（原来没有负责人）
--   change_type=CHANGE  更换负责人（原来有，换成别人）
--   change_type=CLEAR   清除负责人（直接生效，不发起审批）
--
-- 这张表同时承担两件事：
--   1) 组织详情里的「负责人变更记录」时间线——谁把谁换成了谁、什么原因、何时生效；
--   2) 变更审批的业务单据本体。审批发起时 bizId 就是本表 id，回调按它回填终态，
--      因此「改审批模板 / 改组织」都不会让在途单失去落点（记录行本身是快照）。
-- 原负责人 / 新负责人都是提交那一刻的姓名快照：员工改名、离职后记录仍然可读。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ehr_leader_change (
    id                   BIGINT       NOT NULL COMMENT '雪花ID',
    org_id               BIGINT       NOT NULL COMMENT '组织节点ID',
    org_name             VARCHAR(128) DEFAULT '' COMMENT '组织名称快照',
    org_path             VARCHAR(512) DEFAULT '' COMMENT '组织全路径快照',
    org_type             VARCHAR(32)  DEFAULT '' COMMENT '组织类型快照 GROUP/COMPANY/DEPARTMENT/PROJECT/SUB_PROJECT',
    change_type          VARCHAR(16)  NOT NULL DEFAULT 'CHANGE' COMMENT 'SET 首次设置 / CHANGE 更换 / CLEAR 清除',
    old_leader_id        BIGINT       DEFAULT NULL COMMENT '原负责人员工ID（首次设置为空）',
    old_leader_name      VARCHAR(64)  DEFAULT '' COMMENT '原负责人姓名快照',
    new_leader_id        BIGINT       DEFAULT NULL COMMENT '新负责人员工ID（清除时为空）',
    new_leader_name      VARCHAR(64)  DEFAULT '' COMMENT '新负责人姓名快照',
    new_leader_emp_no    VARCHAR(32)  DEFAULT '' COMMENT '新负责人工号快照',
    new_leader_position  VARCHAR(128) DEFAULT '' COMMENT '新负责人职位快照',
    reason               VARCHAR(500) NOT NULL DEFAULT '' COMMENT '变更原因（必填）',
    status               TINYINT      NOT NULL DEFAULT 0 COMMENT '0审批中 1已生效 2已驳回 3已撤销',
    applicant_account_id BIGINT       DEFAULT NULL COMMENT '发起人账号ID',
    applicant_name       VARCHAR(64)  DEFAULT '' COMMENT '发起人姓名快照',
    approval_instance_id BIGINT       DEFAULT NULL COMMENT '审批中心实例ID',
    effective_at         DATETIME     DEFAULT NULL COMMENT '生效时间（审批通过 / 直接生效的时刻）',
    remark               VARCHAR(255) DEFAULT '' COMMENT '备注：驳回原因、发起失败说明等',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_lc_org (org_id, created_at),
    KEY idx_lc_status (status),
    KEY idx_lc_instance (approval_instance_id),
    KEY idx_lc_new_leader (new_leader_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门负责人变更记录（含首次设置/更换/清除，兼作变更审批单据）';

-- ------------------------------------------------------------
-- 人员信息变更单（ehr.emp_update 审批的业务单据本体，三件套：主表 + 字段明细 + 子表行明细）
--
-- 设计要点：
--   1) 一次「变更」提交落一张主表：基本信息改了哪些字段（ehr_emp_change_field）、
--      银行卡/教育背景/工作经历/家庭成员各增删改了哪些行（ehr_emp_change_row）。
--   2) 审批对比页（ROUTE 自定义页面）按这三张表渲染「原数据删除线 / 新数据红色」；
--      同一份数据也会冻结进审批单 formData.change 快照，页面只读快照、不查业务库。
--   3) 审批通过回调时按明细落库：字段级覆盖 ehr_employee，行级按 op 对子表增/删/改。
--      驳回/撤销只改主表 status，档案保持原样。
--   4) 同一员工同时只允许一张在途单（发起前查审批中心 hasRunning），
--      所以回调按 (employee_id, status=0) 定位在途单即可，不必再存实例号。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ehr_emp_change (
    id                   BIGINT       NOT NULL COMMENT '雪花ID',
    employee_id          BIGINT       NOT NULL COMMENT '员工档案ID',
    emp_no               VARCHAR(32)  DEFAULT '' COMMENT '工号快照',
    employee_name        VARCHAR(64)  DEFAULT '' COMMENT '姓名快照',
    org_id               BIGINT       DEFAULT NULL COMMENT '所属组织ID快照',
    org_name             VARCHAR(255) DEFAULT '' COMMENT '所属组织全路径快照',
    status               TINYINT      NOT NULL DEFAULT 0 COMMENT '0审批中 1已生效 2已驳回 3已撤销',
    applicant_account_id BIGINT       DEFAULT NULL COMMENT '提交人账号ID',
    applicant_name       VARCHAR(64)  DEFAULT '' COMMENT '提交人姓名快照',
    base_changes         INT          NOT NULL DEFAULT 0 COMMENT '基本信息变更字段数（列表概览冗余）',
    add_count            INT          NOT NULL DEFAULT 0 COMMENT '子表新增行数（列表概览冗余）',
    mod_count            INT          NOT NULL DEFAULT 0 COMMENT '子表修改行数（列表概览冗余）',
    del_count            INT          NOT NULL DEFAULT 0 COMMENT '子表删除行数（列表概览冗余）',
    approval_instance_id BIGINT       DEFAULT NULL COMMENT '审批中心实例ID（回调幂等比对用；流程结束保留痕迹）',
    remark               VARCHAR(255) DEFAULT '' COMMENT '备注（驳回原因等）',
    deleted              TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ec_emp (employee_id, status),
    KEY idx_ec_status (status),
    KEY idx_ec_instance (approval_instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员信息变更单（主表）';

CREATE TABLE IF NOT EXISTS ehr_emp_change_field (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    change_id   BIGINT       NOT NULL COMMENT '变更单ID（ehr_emp_change.id）',
    field_key   VARCHAR(32)  NOT NULL COMMENT '字段键（与 ehr_employee 列对应，如 mobile）',
    field_label VARCHAR(32)  NOT NULL COMMENT '字段名快照（如 手机号）',
    old_value   VARCHAR(500) DEFAULT '' COMMENT '变更前（提交时档案值；空串表示原本为空）',
    new_value   VARCHAR(500) DEFAULT '' COMMENT '变更后（本次申请值）',
    seq         INT          NOT NULL DEFAULT 0 COMMENT '展示顺序（与变更表单字段顺序一致）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ecf_change (change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员信息变更·基本信息字段明细';

CREATE TABLE IF NOT EXISTS ehr_emp_change_row (
    id         BIGINT       NOT NULL COMMENT '雪花ID',
    change_id  BIGINT       NOT NULL COMMENT '变更单ID（ehr_emp_change.id）',
    list_type  VARCHAR(16)  NOT NULL COMMENT 'BANK 银行卡 / EDUCATION 教育背景 / WORK 工作经历 / FAMILY 家庭成员',
    row_key    VARCHAR(64)  DEFAULT '' COMMENT '原行ID（ehr_emp_bank 等子表主键）；ADD 行为空',
    op         VARCHAR(8)   NOT NULL COMMENT 'ADD 新增 / DEL 删除 / MOD 修改',
    old_json   TEXT         DEFAULT NULL COMMENT '变更前行快照（JSON，含字典展示名；ADD 为 NULL）',
    new_json   TEXT         DEFAULT NULL COMMENT '变更后行快照（JSON，含字典展示名；DEL 为 NULL）',
    seq        INT          NOT NULL DEFAULT 0 COMMENT '展示顺序（沿用档案展示顺序，新增行排最后）',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ecr_change (change_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员信息变更·子表行明细';

-- ------------------------------------------------------------
-- 内部通知（人力中心维护 → 统一账号中心「通知中心」展示）
--   target_type=0 全员（集团）；=1 按部门（target_org_ids 逗号串，展示时含下级部门）
--   status: 0 编辑中 / 1 已发布 / 2 已下架
--   pinned: 1 置顶，列表优先展示
--   target_org_names / publisher_name 都是发布时的名称快照，人员/组织变动后历史通知仍可读
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ehr_announcement (
    id                   BIGINT        NOT NULL COMMENT '雪花ID',
    announcement_no      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '业务编号，按 settings 单号规则生成（前缀 NOTICE）',
    title                VARCHAR(200)  NOT NULL COMMENT '标题',
    summary              VARCHAR(500)  DEFAULT '' COMMENT '摘要（列表展示）',
    category             VARCHAR(32)   NOT NULL DEFAULT 'OTHER' COMMENT 'HOLIDAY节假日/COMMENDATION表彰/DEPARTMENT部门/POLICY制度/APPOINTMENT部门任命/OTHER其他；字典 ehr.announcement_category',
    content              LONGTEXT      DEFAULT NULL COMMENT '富文本正文（HTML）',
    target_type          TINYINT       NOT NULL DEFAULT 0 COMMENT '0全员（集团） 1按部门',
    target_org_ids       VARCHAR(1000) DEFAULT '' COMMENT '目标组织ID，逗号分隔；全员时为空',
    target_org_names     VARCHAR(1000) DEFAULT '' COMMENT '目标组织名称快照，逗号分隔',
    pinned               TINYINT       NOT NULL DEFAULT 0 COMMENT '1置顶',
    status               TINYINT       NOT NULL DEFAULT 0 COMMENT '0编辑中 1已发布 2已下架',
    publish_at           DATETIME      DEFAULT NULL COMMENT '发布时间',
    offline_at           DATETIME      DEFAULT NULL COMMENT '下架时间',
    publisher_account_id BIGINT        DEFAULT NULL COMMENT '发布人账号ID',
    publisher_name       VARCHAR(64)   DEFAULT '' COMMENT '发布人登录名快照',
    read_count           INT           NOT NULL DEFAULT 0 COMMENT '阅读人数',
    target_count         INT           NOT NULL DEFAULT 0 COMMENT '应读人数（发布时快照）',
    remark               VARCHAR(255)  DEFAULT '' COMMENT '备注',
    deleted              TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ann_status (status, pinned, publish_at),
    KEY idx_ann_deleted (deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部通知';

-- 阅读记录：(通知, 账号) 唯一，重复打开只保留首次时间，阅读人数不重复累加
CREATE TABLE IF NOT EXISTS ehr_announcement_read (
    id              BIGINT   NOT NULL COMMENT '雪花ID',
    announcement_id BIGINT   NOT NULL COMMENT '通知ID',
    account_id      BIGINT   NOT NULL COMMENT '账号ID',
    read_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次阅读时间',
    deleted         TINYINT  NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ann_read (announcement_id, account_id),
    KEY idx_ann_read_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内部通知阅读记录';
