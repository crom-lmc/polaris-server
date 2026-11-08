-- ============================================================
-- 配置中心库表结构（独立库 settings，MySQL 5.7+，全部幂等可重跑）
-- 两级模型：cfg_dict_type（类型）→ cfg_dict_item（项），只收口「纯枚举」：
-- 性别/是否/证件类型/民族/婚姻状况/政治面貌。
-- 银行/学历/职位/请假类型/离职原因/家庭成员关系是「业务主数据」（会被业务表按
-- code 引用、要独立加专属字段），放六张真表 biz_bank / biz_education / biz_position /
-- biz_leave_type / biz_resign_reason / biz_family_relation，见下方「种子主数据」。
-- 有外键关联的业务实体（组织架构、文章分类）两边都不进。
-- 种子数据带固定 ID 与 built_in=1：业务表里可能已按这些 value 落库，禁删改编码。
-- ============================================================

CREATE TABLE IF NOT EXISTS cfg_dict_type (
    id          BIGINT       NOT NULL COMMENT '雪花ID（种子数据用固定小ID）',
    code        VARCHAR(64)  NOT NULL COMMENT '类型编码，唯一；如 ehr.leave_type',
    name        VARCHAR(64)  NOT NULL COMMENT '展示名，如「请假类型」',
    app_code    VARCHAR(32)  DEFAULT NULL COMMENT '归属应用 code；NULL=全局字典',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    built_in    TINYINT      NOT NULL DEFAULT 0 COMMENT '1内置(种子,禁删) 0自定义',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type_code (code, deleted),
    KEY idx_dict_type_app (app_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

CREATE TABLE IF NOT EXISTS cfg_dict_item (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    type_code   VARCHAR(64)  NOT NULL COMMENT '所属类型编码（冗余，免联表）',
    value       VARCHAR(64)  NOT NULL COMMENT '取值；业务表里存的就是它',
    label       VARCHAR(64)  NOT NULL COMMENT '展示名',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '同级排序，小的在前',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注/扩展说明',
    built_in    TINYINT      NOT NULL DEFAULT 0 COMMENT '1内置(种子,禁删) 0自定义',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_item_value (type_code, value, deleted),
    KEY idx_dict_item_type (type_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项';

-- ---------------- 种子字典 ----------------
-- 性别（EHR）：value 与 ehr_employee.gender 的 1/2/0 对齐
INSERT INTO cfg_dict_type (id, code, name, app_code, remark, built_in, status) VALUES
    (101, 'ehr.gender',        '性别',           'ehr',     '员工性别；value 与 ehr_employee.gender 数字对齐', 1, 1),
    (105, 'general.yes_no',    '是否',           NULL,      '通用布尔枚举',                                    1, 1),
    (106, 'general.id_type',   '证件类型',       NULL,      '通用证件类型',                                    1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO cfg_dict_item (id, type_code, value, label, sort, status, built_in) VALUES
    (10101, 'ehr.gender', '1', '男', 1, 1, 1),
    (10102, 'ehr.gender', '2', '女', 2, 1, 1),
    (10103, 'ehr.gender', '0', '未知', 3, 1, 1),
    (10501, 'general.yes_no', '1', '是', 1, 1, 1),
    (10502, 'general.yes_no', '0', '否', 2, 1, 1),
    (10601, 'general.id_type', 'ID_CARD',  '居民身份证', 1, 1, 1),
    (10602, 'general.id_type', 'PASSPORT', '护照',       2, 1, 1),
    (10603, 'general.id_type', 'OTHER',    '其他',       3, 1, 1)
ON DUPLICATE KEY UPDATE label = VALUES(label), sort = VALUES(sort);

INSERT IGNORE INTO cfg_dict_type (id, code, name, app_code, remark, built_in, status) VALUES
    (112, 'general.nation',       '民族',       NULL, '56 个民族，官方顺序',          1, 1),
    (113, 'ehr.marital_status',   '婚姻状况',   'ehr', '未婚/已婚/离异/丧偶',          1, 1),
    (114, 'ehr.political_status', '政治面貌',   'ehr', '群众/党员/团员等',             1, 1);

INSERT IGNORE INTO cfg_dict_item (id, type_code, value, label, sort, status, built_in) VALUES
    -- 学历
    -- 银行
    -- 婚姻状况
    (11301, 'ehr.marital_status', 'unmarried', '未婚', 1, 1, 1),
    (11302, 'ehr.marital_status', 'married',   '已婚', 2, 1, 1),
    (11303, 'ehr.marital_status', 'divorced',  '离异', 3, 1, 1),
    (11304, 'ehr.marital_status', 'widowed',   '丧偶', 4, 1, 1),
    -- 政治面貌
    (11401, 'ehr.political_status', 'masses',      '群众',       1, 1, 1),
    (11402, 'ehr.political_status', 'cpc',         '中共党员',   2, 1, 1),
    (11403, 'ehr.political_status', 'ccyl',        '共青团员',   3, 1, 1),
    (11404, 'ehr.political_status', 'dpa',         '民主党派',   4, 1, 1),
    (11405, 'ehr.political_status', 'nonpartisan', '无党派人士', 5, 1, 1);

-- 通知类型（EHR）：value 与 ehr_announcement.category 对齐，新增类型只改字典即可
INSERT IGNORE INTO cfg_dict_type (id, code, name, app_code, remark, built_in, status) VALUES
    (115, 'ehr.announcement_category', '通知类型', 'ehr', '内部通知分类；value 与 ehr_announcement.category 对齐', 1, 1);

INSERT IGNORE INTO cfg_dict_item (id, type_code, value, label, sort, status, built_in) VALUES
    (11501, 'ehr.announcement_category', 'HOLIDAY',      '节假日通知', 1, 1, 1),
    (11502, 'ehr.announcement_category', 'COMMENDATION', '表彰通知',   2, 1, 1),
    (11503, 'ehr.announcement_category', 'DEPARTMENT',   '部门通知',   3, 1, 1),
    (11504, 'ehr.announcement_category', 'POLICY',       '制度通知',   4, 1, 1),
    (11505, 'ehr.announcement_category', 'APPOINTMENT',  '部门任命',   5, 1, 1),
    (11506, 'ehr.announcement_category', 'OTHER',        '其他',       6, 1, 1);

-- 民族（56 个，官方顺序；value 用拼音）
INSERT IGNORE INTO cfg_dict_item (id, type_code, value, label, sort, status, built_in) VALUES
    (11201, 'general.nation', 'han',        '汉族',       1, 1, 1),
    (11202, 'general.nation', 'menggu',     '蒙古族',     2, 1, 1),
    (11203, 'general.nation', 'hui',        '回族',       3, 1, 1),
    (11204, 'general.nation', 'zang',       '藏族',       4, 1, 1),
    (11205, 'general.nation', 'weiwuer',    '维吾尔族',   5, 1, 1),
    (11206, 'general.nation', 'miao',       '苗族',       6, 1, 1),
    (11207, 'general.nation', 'yi',         '彝族',       7, 1, 1),
    (11208, 'general.nation', 'zhuang',     '壮族',       8, 1, 1),
    (11209, 'general.nation', 'buyi',       '布依族',     9, 1, 1),
    (11210, 'general.nation', 'chaoxian',   '朝鲜族',     10, 1, 1),
    (11211, 'general.nation', 'man',        '满族',       11, 1, 1),
    (11212, 'general.nation', 'dong',       '侗族',       12, 1, 1),
    (11213, 'general.nation', 'yao',        '瑶族',       13, 1, 1),
    (11214, 'general.nation', 'bai',        '白族',       14, 1, 1),
    (11215, 'general.nation', 'tujia',      '土家族',     15, 1, 1),
    (11216, 'general.nation', 'hani',       '哈尼族',     16, 1, 1),
    (11217, 'general.nation', 'kazake',     '哈萨克族',   17, 1, 1),
    (11218, 'general.nation', 'dai',        '傣族',       18, 1, 1),
    (11219, 'general.nation', 'li',         '黎族',       19, 1, 1),
    (11220, 'general.nation', 'lisu',       '傈僳族',     20, 1, 1),
    (11221, 'general.nation', 'wa',         '佤族',       21, 1, 1),
    (11222, 'general.nation', 'she',        '畲族',       22, 1, 1),
    (11223, 'general.nation', 'gaoshan',    '高山族',     23, 1, 1),
    (11224, 'general.nation', 'lahu',       '拉祜族',     24, 1, 1),
    (11225, 'general.nation', 'shui',       '水族',       25, 1, 1),
    (11226, 'general.nation', 'dongxiang',  '东乡族',     26, 1, 1),
    (11227, 'general.nation', 'naxi',       '纳西族',     27, 1, 1),
    (11228, 'general.nation', 'jingpo',     '景颇族',     28, 1, 1),
    (11229, 'general.nation', 'keerkezi',   '柯尔克孜族', 29, 1, 1),
    (11230, 'general.nation', 'tu',         '土族',       30, 1, 1),
    (11231, 'general.nation', 'dawoer',     '达斡尔族',   31, 1, 1),
    (11232, 'general.nation', 'mulao',      '仫佬族',     32, 1, 1),
    (11233, 'general.nation', 'qiang',      '羌族',       33, 1, 1),
    (11234, 'general.nation', 'bulang',     '布朗族',     34, 1, 1),
    (11235, 'general.nation', 'sala',       '撒拉族',     35, 1, 1),
    (11236, 'general.nation', 'maonan',     '毛南族',     36, 1, 1),
    (11237, 'general.nation', 'gelao',      '仡佬族',     37, 1, 1),
    (11238, 'general.nation', 'xibo',       '锡伯族',     38, 1, 1),
    (11239, 'general.nation', 'achang',     '阿昌族',     39, 1, 1),
    (11240, 'general.nation', 'pumi',       '普米族',     40, 1, 1),
    (11241, 'general.nation', 'tajike',     '塔吉克族',   41, 1, 1),
    (11242, 'general.nation', 'nu',         '怒族',       42, 1, 1),
    (11243, 'general.nation', 'wuzibieke',  '乌孜别克族', 43, 1, 1),
    (11244, 'general.nation', 'eluosi',     '俄罗斯族',   44, 1, 1),
    (11245, 'general.nation', 'ewenke',     '鄂温克族',   45, 1, 1),
    (11246, 'general.nation', 'deang',      '德昂族',     46, 1, 1),
    (11247, 'general.nation', 'baoan',      '保安族',     47, 1, 1),
    (11248, 'general.nation', 'yugu',       '裕固族',     48, 1, 1),
    (11249, 'general.nation', 'jing',       '京族',       49, 1, 1),
    (11250, 'general.nation', 'tataer',     '塔塔尔族',   50, 1, 1),
    (11251, 'general.nation', 'dulong',     '独龙族',     51, 1, 1),
    (11252, 'general.nation', 'elunchun',   '鄂伦春族',   52, 1, 1),
    (11253, 'general.nation', 'hezhe',      '赫哲族',     53, 1, 1),
    (11254, 'general.nation', 'menba',      '门巴族',     54, 1, 1),
    (11255, 'general.nation', 'luoba',      '珞巴族',     55, 1, 1),
    (11256, 'general.nation', 'jinuo',      '基诺族',     56, 1, 1);

-- 人员档案扩展字典 end

-- ============================================================
-- 业务主数据（六张真表，独立于字典）
-- 银行/学历/职位/请假类型/离职原因/家庭成员关系：会被业务表按 code 引用，
-- 需要独立加专属字段（银行的联行号、职位的职级等），所以各建真表。
-- 跨服务读取 /internal/biz/{type}；管理端 /api/settings/biz/**。
-- 种子保留原字典项 ID（迁移自 cfg_dict_item，见 tools/split-biz-tables.sql）。
-- ============================================================

CREATE TABLE IF NOT EXISTS biz_bank (
    id          BIGINT       NOT NULL COMMENT '雪花ID（种子数据用固定小ID）',
    code        VARCHAR(64)  NOT NULL COMMENT '取值编码；业务表里存的就是它，如 icbc',
    name        VARCHAR(128) NOT NULL COMMENT '展示名，如 工商银行',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '同级排序，小的在前',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注/扩展字段占位',
    built_in    TINYINT      NOT NULL DEFAULT 0 COMMENT '1内置(种子,禁删) 0自定义',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_bank_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='主数据：银行';

CREATE TABLE IF NOT EXISTS biz_education LIKE biz_bank;
ALTER TABLE biz_education COMMENT='主数据：学历';
CREATE TABLE IF NOT EXISTS biz_position LIKE biz_bank;
ALTER TABLE biz_position COMMENT='主数据：职位';
-- 职位需要「类别」维度（管理类/技术类），其它五类不需要，故单独加列
SET @pos_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'biz_position' AND column_name = 'category');
SET @pos_sql = IF(@pos_col = 0,
    "ALTER TABLE biz_position ADD COLUMN category VARCHAR(32) DEFAULT NULL COMMENT '职位类别：管理类/技术类' AFTER remark",
    'SELECT 1');
PREPARE pos_stmt FROM @pos_sql;
EXECUTE pos_stmt;
DEALLOCATE PREPARE pos_stmt;
-- 职级体系：职务族 + 序列 + 等级。同一个职务族是一条晋升通道
-- （开发工程师：初级 → 中级 → 高级 → 资深），前端职位下拉按职务族分组、按等级排序。
SET @c = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'biz_position' AND column_name = 'job_family');
SET @s = IF(@c = 0,
    "ALTER TABLE biz_position
       ADD COLUMN job_family VARCHAR(32) DEFAULT NULL COMMENT '职务族编码 DEV/TEST/ACC/RECRUIT/MGMT' AFTER category,
       ADD COLUMN job_family_name VARCHAR(64) DEFAULT NULL COMMENT '职务族展示名（开发工程师/会计/招聘专员）' AFTER job_family,
       ADD COLUMN series VARCHAR(2) DEFAULT NULL COMMENT '序列 M管理/P专业' AFTER job_family_name,
       ADD COLUMN level_no INT DEFAULT NULL COMMENT '等级序号 P:1助理2初级3中级4高级5资深 M:1经理2总监3高管' AFTER series,
       ADD COLUMN level_name VARCHAR(16) DEFAULT NULL COMMENT '等级展示名' AFTER level_no",
    'SELECT 1');
PREPARE pos_stmt2 FROM @s;
EXECUTE pos_stmt2;
DEALLOCATE PREPARE pos_stmt2;
CREATE TABLE IF NOT EXISTS biz_leave_type LIKE biz_bank;
ALTER TABLE biz_leave_type COMMENT='主数据：请假类型';
CREATE TABLE IF NOT EXISTS biz_resign_reason LIKE biz_bank;
ALTER TABLE biz_resign_reason COMMENT='主数据：离职/减员原因';
CREATE TABLE IF NOT EXISTS biz_family_relation LIKE biz_bank;
ALTER TABLE biz_family_relation COMMENT='主数据：家庭成员关系';

-- 安全配置：全系统只有一份，单行存储（id 固定 1）。
-- 密码规则改为多选（数字/大写/小写/特殊字符），短信相关一律改为邮箱。
CREATE TABLE IF NOT EXISTS cfg_security_config (
    id BIGINT NOT NULL COMMENT '固定 1（单行配置）',
    initial_password_mode VARCHAR(16) NOT NULL DEFAULT 'FIXED' COMMENT '初始密码规则 RANDOM 随机 / FIXED 固定',
    default_password VARCHAR(64) NOT NULL DEFAULT 'G#191975' COMMENT '固定初始密码',
    password_rules VARCHAR(64) NOT NULL DEFAULT 'DIGIT,UPPER,LOWER,SPECIAL' COMMENT '密码必须包含的字符类型，逗号分隔：DIGIT 数字 / UPPER 大写 / LOWER 小写 / SPECIAL 特殊字符',
    password_min_length INT NOT NULL DEFAULT 6 COMMENT '密码最小长度',
    password_max_length INT NOT NULL DEFAULT 15 COMMENT '密码最大长度',
    password_lock_mode VARCHAR(16) NOT NULL DEFAULT 'EMAIL' COMMENT '修改密码验证方式 ACCOUNT 账号密码锁 / EMAIL 邮箱验证锁',
    login_fail_limit INT NOT NULL DEFAULT 3 COMMENT '登录密码输入失败次数',
    login_fail_lock_minutes INT NOT NULL DEFAULT 15 COMMENT '登录失败锁定时间（分钟）',
    login_fail_detect_count INT NOT NULL DEFAULT 5 COMMENT '连续登录检测次数',
    force_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '首次登录强制修改密码 1 启用 / 0 不启用',
    session_timeout_minutes INT NOT NULL DEFAULT 420 COMMENT '登录过期时间（分钟）',
    max_login_error_count INT NOT NULL DEFAULT 5 COMMENT '最大登录错误次数',
    user_lock_minutes INT NOT NULL DEFAULT 10 COMMENT '用户锁定时长（分钟）',
    email_code_digits INT NOT NULL DEFAULT 6 COMMENT '邮箱验证码位数',
    verify_code_expire_minutes INT NOT NULL DEFAULT 10 COMMENT '验证码过期时间（分钟）',
    updated_by VARCHAR(64) DEFAULT NULL COMMENT '最后修改人',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全配置（单行，id=1）';

-- 幂等补默认行：只在缺失时插入，已有配置不会被覆盖
INSERT IGNORE INTO cfg_security_config (id) VALUES (1);

-- ---------------- 种子主数据（固定 ID + built_in=1，禁删改编码） ----------------
-- 职位：职级体系（管理序列 + 专业序列）。同一个职务族是一条晋升通道，
-- 例如 开发工程师：助理 → 初级 → 中级 → 高级 → 资深；前端下拉按职务族分组、按等级排序。
-- 岗位名只体现序列与层级，不描述具体工作内容（细分职责交给部门/岗位说明书）。
INSERT IGNORE INTO biz_position
    (id, code, name, sort, status, remark, built_in, category,
     job_family, job_family_name, series, level_no, level_name) VALUES
    (12001, 'M_GROUP_CEO', '集团CEO', 101, 1, '管理序列 · 高管', 1, '管理类', 'MGMT', '管理序列', 'M', 3, '高管'),
    (12002, 'M_GM', '总经理', 102, 1, '管理序列 · 高管', 1, '管理类', 'MGMT', '管理序列', 'M', 3, '高管'),
    (12003, 'M_VGM', '副总经理', 103, 1, '管理序列 · 高管', 1, '管理类', 'MGMT', '管理序列', 'M', 3, '高管'),
    (12004, 'M_FIN_DIR', '财务总监', 111, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12005, 'M_HR_DIR', '人力资源总监', 112, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12006, 'M_RD_DIR', '研发总监', 113, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12007, 'M_TECH_DIR', '技术总监', 114, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12008, 'M_PROD_DIR', '产品总监', 115, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12009, 'M_MKT_DIR', '市场总监', 116, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12010, 'M_STRAT_DIR', '战略总监', 117, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12011, 'M_OPS_DIR', '运营总监', 118, 1, '管理序列 · 总监', 1, '管理类', 'MGMT', '管理序列', 'M', 2, '总监'),
    (12012, 'M_FIN_MGR', '财务经理', 131, 1, '管理序列 · 经理', 1, '管理类', 'MGMT', '管理序列', 'M', 1, '经理'),
    (12013, 'M_HR_MGR', '人事经理', 132, 1, '管理序列 · 经理', 1, '管理类', 'MGMT', '管理序列', 'M', 1, '经理'),
    (12014, 'M_RECRUIT_MGR', '招聘经理', 133, 1, '管理序列 · 经理', 1, '管理类', 'MGMT', '管理序列', 'M', 1, '经理'),
    (12015, 'M_MKT_MGR', '市场经理', 134, 1, '管理序列 · 经理', 1, '管理类', 'MGMT', '管理序列', 'M', 1, '经理'),
    (12016, 'M_OPS_MGR', '运营经理', 135, 1, '管理序列 · 经理', 1, '管理类', 'MGMT', '管理序列', 'M', 1, '经理'),
    (12017, 'M_TECH_MGR', '技术经理', 136, 1, '管理序列 · 经理', 1, '管理类', 'MGMT', '管理序列', 'M', 1, '经理'),
    (12018, 'DEV_P1', '助理开发工程师', 1011, 1, '开发工程师 · 助理', 1, '技术类', 'DEV', '开发工程师', 'P', 1, '助理'),
    (12019, 'DEV_P2', '初级开发工程师', 1012, 1, '开发工程师 · 初级', 1, '技术类', 'DEV', '开发工程师', 'P', 2, '初级'),
    (12020, 'DEV_P3', '中级开发工程师', 1013, 1, '开发工程师 · 中级', 1, '技术类', 'DEV', '开发工程师', 'P', 3, '中级'),
    (12021, 'DEV_P4', '高级开发工程师', 1014, 1, '开发工程师 · 高级', 1, '技术类', 'DEV', '开发工程师', 'P', 4, '高级'),
    (12022, 'DEV_P5', '资深开发工程师', 1015, 1, '开发工程师 · 资深', 1, '技术类', 'DEV', '开发工程师', 'P', 5, '资深'),
    (12023, 'TEST_P1', '助理测试工程师', 1021, 1, '测试工程师 · 助理', 1, '技术类', 'TEST', '测试工程师', 'P', 1, '助理'),
    (12024, 'TEST_P2', '初级测试工程师', 1022, 1, '测试工程师 · 初级', 1, '技术类', 'TEST', '测试工程师', 'P', 2, '初级'),
    (12025, 'TEST_P3', '中级测试工程师', 1023, 1, '测试工程师 · 中级', 1, '技术类', 'TEST', '测试工程师', 'P', 3, '中级'),
    (12026, 'TEST_P4', '高级测试工程师', 1024, 1, '测试工程师 · 高级', 1, '技术类', 'TEST', '测试工程师', 'P', 4, '高级'),
    (12027, 'TEST_P5', '资深测试工程师', 1025, 1, '测试工程师 · 资深', 1, '技术类', 'TEST', '测试工程师', 'P', 5, '资深'),
    (12028, 'OPS_P1', '助理运维工程师', 1031, 1, '运维工程师 · 助理', 1, '技术类', 'OPS', '运维工程师', 'P', 1, '助理'),
    (12029, 'OPS_P2', '初级运维工程师', 1032, 1, '运维工程师 · 初级', 1, '技术类', 'OPS', '运维工程师', 'P', 2, '初级'),
    (12030, 'OPS_P3', '中级运维工程师', 1033, 1, '运维工程师 · 中级', 1, '技术类', 'OPS', '运维工程师', 'P', 3, '中级'),
    (12031, 'OPS_P4', '高级运维工程师', 1034, 1, '运维工程师 · 高级', 1, '技术类', 'OPS', '运维工程师', 'P', 4, '高级'),
    (12032, 'OPS_P5', '资深运维工程师', 1035, 1, '运维工程师 · 资深', 1, '技术类', 'OPS', '运维工程师', 'P', 5, '资深'),
    (12033, 'ALGO_P1', '助理算法工程师', 1041, 1, '算法工程师 · 助理', 1, '技术类', 'ALGO', '算法工程师', 'P', 1, '助理'),
    (12034, 'ALGO_P2', '初级算法工程师', 1042, 1, '算法工程师 · 初级', 1, '技术类', 'ALGO', '算法工程师', 'P', 2, '初级'),
    (12035, 'ALGO_P3', '中级算法工程师', 1043, 1, '算法工程师 · 中级', 1, '技术类', 'ALGO', '算法工程师', 'P', 3, '中级'),
    (12036, 'ALGO_P4', '高级算法工程师', 1044, 1, '算法工程师 · 高级', 1, '技术类', 'ALGO', '算法工程师', 'P', 4, '高级'),
    (12037, 'ALGO_P5', '资深算法工程师', 1045, 1, '算法工程师 · 资深', 1, '技术类', 'ALGO', '算法工程师', 'P', 5, '资深'),
    (12038, 'PM_P1', '助理产品经理', 1051, 1, '产品经理 · 助理', 1, '技术类', 'PM', '产品经理', 'P', 1, '助理'),
    (12039, 'PM_P2', '初级产品经理', 1052, 1, '产品经理 · 初级', 1, '技术类', 'PM', '产品经理', 'P', 2, '初级'),
    (12040, 'PM_P3', '中级产品经理', 1053, 1, '产品经理 · 中级', 1, '技术类', 'PM', '产品经理', 'P', 3, '中级'),
    (12041, 'PM_P4', '高级产品经理', 1054, 1, '产品经理 · 高级', 1, '技术类', 'PM', '产品经理', 'P', 4, '高级'),
    (12042, 'PM_P5', '资深产品经理', 1055, 1, '产品经理 · 资深', 1, '技术类', 'PM', '产品经理', 'P', 5, '资深'),
    (12043, 'UX_P1', '助理设计师', 1061, 1, '设计师 · 助理', 1, '技术类', 'UX', '设计师', 'P', 1, '助理'),
    (12044, 'UX_P2', '初级设计师', 1062, 1, '设计师 · 初级', 1, '技术类', 'UX', '设计师', 'P', 2, '初级'),
    (12045, 'UX_P3', '中级设计师', 1063, 1, '设计师 · 中级', 1, '技术类', 'UX', '设计师', 'P', 3, '中级'),
    (12046, 'UX_P4', '高级设计师', 1064, 1, '设计师 · 高级', 1, '技术类', 'UX', '设计师', 'P', 4, '高级'),
    (12047, 'UX_P5', '资深设计师', 1065, 1, '设计师 · 资深', 1, '技术类', 'UX', '设计师', 'P', 5, '资深'),
    (12048, 'DA_P1', '助理数据分析师', 1071, 1, '数据分析师 · 助理', 1, '技术类', 'DA', '数据分析师', 'P', 1, '助理'),
    (12049, 'DA_P2', '初级数据分析师', 1072, 1, '数据分析师 · 初级', 1, '技术类', 'DA', '数据分析师', 'P', 2, '初级'),
    (12050, 'DA_P3', '中级数据分析师', 1073, 1, '数据分析师 · 中级', 1, '技术类', 'DA', '数据分析师', 'P', 3, '中级'),
    (12051, 'DA_P4', '高级数据分析师', 1074, 1, '数据分析师 · 高级', 1, '技术类', 'DA', '数据分析师', 'P', 4, '高级'),
    (12052, 'DA_P5', '资深数据分析师', 1075, 1, '数据分析师 · 资深', 1, '技术类', 'DA', '数据分析师', 'P', 5, '资深'),
    (12053, 'ACC_P1', '助理会计', 2011, 1, '会计 · 助理', 1, '财务类', 'ACC', '会计', 'P', 1, '助理'),
    (12054, 'ACC_P2', '初级会计', 2012, 1, '会计 · 初级', 1, '财务类', 'ACC', '会计', 'P', 2, '初级'),
    (12055, 'ACC_P3', '中级会计', 2013, 1, '会计 · 中级', 1, '财务类', 'ACC', '会计', 'P', 3, '中级'),
    (12056, 'ACC_P4', '高级会计', 2014, 1, '会计 · 高级', 1, '财务类', 'ACC', '会计', 'P', 4, '高级'),
    (12057, 'ACC_P5', '资深会计', 2015, 1, '会计 · 资深', 1, '财务类', 'ACC', '会计', 'P', 5, '资深'),
    (12058, 'CASHIER_P1', '助理出纳', 2021, 1, '出纳 · 助理', 1, '财务类', 'CASHIER', '出纳', 'P', 1, '助理'),
    (12059, 'CASHIER_P2', '初级出纳', 2022, 1, '出纳 · 初级', 1, '财务类', 'CASHIER', '出纳', 'P', 2, '初级'),
    (12060, 'CASHIER_P3', '中级出纳', 2023, 1, '出纳 · 中级', 1, '财务类', 'CASHIER', '出纳', 'P', 3, '中级'),
    (12061, 'CASHIER_P4', '高级出纳', 2024, 1, '出纳 · 高级', 1, '财务类', 'CASHIER', '出纳', 'P', 4, '高级'),
    (12062, 'CASHIER_P5', '资深出纳', 2025, 1, '出纳 · 资深', 1, '财务类', 'CASHIER', '出纳', 'P', 5, '资深'),
    (12063, 'TAX_P1', '助理税务专员', 2031, 1, '税务专员 · 助理', 1, '财务类', 'TAX', '税务专员', 'P', 1, '助理'),
    (12064, 'TAX_P2', '初级税务专员', 2032, 1, '税务专员 · 初级', 1, '财务类', 'TAX', '税务专员', 'P', 2, '初级'),
    (12065, 'TAX_P3', '中级税务专员', 2033, 1, '税务专员 · 中级', 1, '财务类', 'TAX', '税务专员', 'P', 3, '中级'),
    (12066, 'TAX_P4', '高级税务专员', 2034, 1, '税务专员 · 高级', 1, '财务类', 'TAX', '税务专员', 'P', 4, '高级'),
    (12067, 'TAX_P5', '资深税务专员', 2035, 1, '税务专员 · 资深', 1, '财务类', 'TAX', '税务专员', 'P', 5, '资深'),
    (12068, 'TREASURY_P1', '助理资金专员', 2041, 1, '资金专员 · 助理', 1, '财务类', 'TREASURY', '资金专员', 'P', 1, '助理'),
    (12069, 'TREASURY_P2', '初级资金专员', 2042, 1, '资金专员 · 初级', 1, '财务类', 'TREASURY', '资金专员', 'P', 2, '初级'),
    (12070, 'TREASURY_P3', '中级资金专员', 2043, 1, '资金专员 · 中级', 1, '财务类', 'TREASURY', '资金专员', 'P', 3, '中级'),
    (12071, 'TREASURY_P4', '高级资金专员', 2044, 1, '资金专员 · 高级', 1, '财务类', 'TREASURY', '资金专员', 'P', 4, '高级'),
    (12072, 'TREASURY_P5', '资深资金专员', 2045, 1, '资金专员 · 资深', 1, '财务类', 'TREASURY', '资金专员', 'P', 5, '资深'),
    (12073, 'BUDGET_P1', '助理预算专员', 2051, 1, '预算专员 · 助理', 1, '财务类', 'BUDGET', '预算专员', 'P', 1, '助理'),
    (12074, 'BUDGET_P2', '初级预算专员', 2052, 1, '预算专员 · 初级', 1, '财务类', 'BUDGET', '预算专员', 'P', 2, '初级'),
    (12075, 'BUDGET_P3', '中级预算专员', 2053, 1, '预算专员 · 中级', 1, '财务类', 'BUDGET', '预算专员', 'P', 3, '中级'),
    (12076, 'BUDGET_P4', '高级预算专员', 2054, 1, '预算专员 · 高级', 1, '财务类', 'BUDGET', '预算专员', 'P', 4, '高级'),
    (12077, 'BUDGET_P5', '资深预算专员', 2055, 1, '预算专员 · 资深', 1, '财务类', 'BUDGET', '预算专员', 'P', 5, '资深'),
    (12078, 'RECRUIT_P1', '助理招聘专员', 3011, 1, '招聘专员 · 助理', 1, '职能类', 'RECRUIT', '招聘专员', 'P', 1, '助理'),
    (12079, 'RECRUIT_P2', '初级招聘专员', 3012, 1, '招聘专员 · 初级', 1, '职能类', 'RECRUIT', '招聘专员', 'P', 2, '初级'),
    (12080, 'RECRUIT_P3', '中级招聘专员', 3013, 1, '招聘专员 · 中级', 1, '职能类', 'RECRUIT', '招聘专员', 'P', 3, '中级'),
    (12081, 'RECRUIT_P4', '高级招聘专员', 3014, 1, '招聘专员 · 高级', 1, '职能类', 'RECRUIT', '招聘专员', 'P', 4, '高级'),
    (12082, 'RECRUIT_P5', '资深招聘专员', 3015, 1, '招聘专员 · 资深', 1, '职能类', 'RECRUIT', '招聘专员', 'P', 5, '资深'),
    (12083, 'COMP_P1', '助理薪酬绩效专员', 3021, 1, '薪酬绩效专员 · 助理', 1, '职能类', 'COMP', '薪酬绩效专员', 'P', 1, '助理'),
    (12084, 'COMP_P2', '初级薪酬绩效专员', 3022, 1, '薪酬绩效专员 · 初级', 1, '职能类', 'COMP', '薪酬绩效专员', 'P', 2, '初级'),
    (12085, 'COMP_P3', '中级薪酬绩效专员', 3023, 1, '薪酬绩效专员 · 中级', 1, '职能类', 'COMP', '薪酬绩效专员', 'P', 3, '中级'),
    (12086, 'COMP_P4', '高级薪酬绩效专员', 3024, 1, '薪酬绩效专员 · 高级', 1, '职能类', 'COMP', '薪酬绩效专员', 'P', 4, '高级'),
    (12087, 'COMP_P5', '资深薪酬绩效专员', 3025, 1, '薪酬绩效专员 · 资深', 1, '职能类', 'COMP', '薪酬绩效专员', 'P', 5, '资深'),
    (12088, 'ER_P1', '助理员工关系专员', 3031, 1, '员工关系专员 · 助理', 1, '职能类', 'ER', '员工关系专员', 'P', 1, '助理'),
    (12089, 'ER_P2', '初级员工关系专员', 3032, 1, '员工关系专员 · 初级', 1, '职能类', 'ER', '员工关系专员', 'P', 2, '初级'),
    (12090, 'ER_P3', '中级员工关系专员', 3033, 1, '员工关系专员 · 中级', 1, '职能类', 'ER', '员工关系专员', 'P', 3, '中级'),
    (12091, 'ER_P4', '高级员工关系专员', 3034, 1, '员工关系专员 · 高级', 1, '职能类', 'ER', '员工关系专员', 'P', 4, '高级'),
    (12092, 'ER_P5', '资深员工关系专员', 3035, 1, '员工关系专员 · 资深', 1, '职能类', 'ER', '员工关系专员', 'P', 5, '资深'),
    (12093, 'TRAINING_P1', '助理培训专员', 3041, 1, '培训专员 · 助理', 1, '职能类', 'TRAINING', '培训专员', 'P', 1, '助理'),
    (12094, 'TRAINING_P2', '初级培训专员', 3042, 1, '培训专员 · 初级', 1, '职能类', 'TRAINING', '培训专员', 'P', 2, '初级'),
    (12095, 'TRAINING_P3', '中级培训专员', 3043, 1, '培训专员 · 中级', 1, '职能类', 'TRAINING', '培训专员', 'P', 3, '中级'),
    (12096, 'TRAINING_P4', '高级培训专员', 3044, 1, '培训专员 · 高级', 1, '职能类', 'TRAINING', '培训专员', 'P', 4, '高级'),
    (12097, 'TRAINING_P5', '资深培训专员', 3045, 1, '培训专员 · 资深', 1, '职能类', 'TRAINING', '培训专员', 'P', 5, '资深'),
    (12098, 'OPS_SPEC_P1', '助理运营专员', 3051, 1, '运营专员 · 助理', 1, '职能类', 'OPS_SPEC', '运营专员', 'P', 1, '助理'),
    (12099, 'OPS_SPEC_P2', '初级运营专员', 3052, 1, '运营专员 · 初级', 1, '职能类', 'OPS_SPEC', '运营专员', 'P', 2, '初级'),
    (12100, 'OPS_SPEC_P3', '中级运营专员', 3053, 1, '运营专员 · 中级', 1, '职能类', 'OPS_SPEC', '运营专员', 'P', 3, '中级'),
    (12101, 'OPS_SPEC_P4', '高级运营专员', 3054, 1, '运营专员 · 高级', 1, '职能类', 'OPS_SPEC', '运营专员', 'P', 4, '高级'),
    (12102, 'OPS_SPEC_P5', '资深运营专员', 3055, 1, '运营专员 · 资深', 1, '职能类', 'OPS_SPEC', '运营专员', 'P', 5, '资深'),
    (12103, 'USER_OPS_P1', '助理用户运营专员', 3061, 1, '用户运营专员 · 助理', 1, '职能类', 'USER_OPS', '用户运营专员', 'P', 1, '助理'),
    (12104, 'USER_OPS_P2', '初级用户运营专员', 3062, 1, '用户运营专员 · 初级', 1, '职能类', 'USER_OPS', '用户运营专员', 'P', 2, '初级'),
    (12105, 'USER_OPS_P3', '中级用户运营专员', 3063, 1, '用户运营专员 · 中级', 1, '职能类', 'USER_OPS', '用户运营专员', 'P', 3, '中级'),
    (12106, 'USER_OPS_P4', '高级用户运营专员', 3064, 1, '用户运营专员 · 高级', 1, '职能类', 'USER_OPS', '用户运营专员', 'P', 4, '高级'),
    (12107, 'USER_OPS_P5', '资深用户运营专员', 3065, 1, '用户运营专员 · 资深', 1, '职能类', 'USER_OPS', '用户运营专员', 'P', 5, '资深'),
    (12108, 'CONTENT_OPS_P1', '助理内容运营专员', 3071, 1, '内容运营专员 · 助理', 1, '职能类', 'CONTENT_OPS', '内容运营专员', 'P', 1, '助理'),
    (12109, 'CONTENT_OPS_P2', '初级内容运营专员', 3072, 1, '内容运营专员 · 初级', 1, '职能类', 'CONTENT_OPS', '内容运营专员', 'P', 2, '初级'),
    (12110, 'CONTENT_OPS_P3', '中级内容运营专员', 3073, 1, '内容运营专员 · 中级', 1, '职能类', 'CONTENT_OPS', '内容运营专员', 'P', 3, '中级'),
    (12111, 'CONTENT_OPS_P4', '高级内容运营专员', 3074, 1, '内容运营专员 · 高级', 1, '职能类', 'CONTENT_OPS', '内容运营专员', 'P', 4, '高级'),
    (12112, 'CONTENT_OPS_P5', '资深内容运营专员', 3075, 1, '内容运营专员 · 资深', 1, '职能类', 'CONTENT_OPS', '内容运营专员', 'P', 5, '资深'),
    (12113, 'DATA_OPS_P1', '助理数据运营专员', 3081, 1, '数据运营专员 · 助理', 1, '职能类', 'DATA_OPS', '数据运营专员', 'P', 1, '助理'),
    (12114, 'DATA_OPS_P2', '初级数据运营专员', 3082, 1, '数据运营专员 · 初级', 1, '职能类', 'DATA_OPS', '数据运营专员', 'P', 2, '初级'),
    (12115, 'DATA_OPS_P3', '中级数据运营专员', 3083, 1, '数据运营专员 · 中级', 1, '职能类', 'DATA_OPS', '数据运营专员', 'P', 3, '中级'),
    (12116, 'DATA_OPS_P4', '高级数据运营专员', 3084, 1, '数据运营专员 · 高级', 1, '职能类', 'DATA_OPS', '数据运营专员', 'P', 4, '高级'),
    (12117, 'DATA_OPS_P5', '资深数据运营专员', 3085, 1, '数据运营专员 · 资深', 1, '职能类', 'DATA_OPS', '数据运营专员', 'P', 5, '资深'),
    (12118, 'MARKET_P1', '助理市场专员', 4011, 1, '市场专员 · 助理', 1, '市场类', 'MARKET', '市场专员', 'P', 1, '助理'),
    (12119, 'MARKET_P2', '初级市场专员', 4012, 1, '市场专员 · 初级', 1, '市场类', 'MARKET', '市场专员', 'P', 2, '初级'),
    (12120, 'MARKET_P3', '中级市场专员', 4013, 1, '市场专员 · 中级', 1, '市场类', 'MARKET', '市场专员', 'P', 3, '中级'),
    (12121, 'MARKET_P4', '高级市场专员', 4014, 1, '市场专员 · 高级', 1, '市场类', 'MARKET', '市场专员', 'P', 4, '高级'),
    (12122, 'MARKET_P5', '资深市场专员', 4015, 1, '市场专员 · 资深', 1, '市场类', 'MARKET', '市场专员', 'P', 5, '资深'),
    (12123, 'BIZ_P1', '助理商务专员', 4021, 1, '商务专员 · 助理', 1, '市场类', 'BIZ', '商务专员', 'P', 1, '助理'),
    (12124, 'BIZ_P2', '初级商务专员', 4022, 1, '商务专员 · 初级', 1, '市场类', 'BIZ', '商务专员', 'P', 2, '初级'),
    (12125, 'BIZ_P3', '中级商务专员', 4023, 1, '商务专员 · 中级', 1, '市场类', 'BIZ', '商务专员', 'P', 3, '中级'),
    (12126, 'BIZ_P4', '高级商务专员', 4024, 1, '商务专员 · 高级', 1, '市场类', 'BIZ', '商务专员', 'P', 4, '高级'),
    (12127, 'BIZ_P5', '资深商务专员', 4025, 1, '商务专员 · 资深', 1, '市场类', 'BIZ', '商务专员', 'P', 5, '资深');

INSERT IGNORE INTO biz_leave_type (id, code, name, sort, status, built_in) VALUES
    (10301, 'ANNUAL',       '年假',         1, 1, 1),
    (10302, 'SICK',         '病假',         2, 1, 1),
    (10303, 'PERSONAL',     '事假',         3, 1, 1),
    (10304, 'MARRIAGE',     '婚假',         4, 1, 1),
    (10305, 'MATERNITY',    '产假/陪产假',  5, 1, 1),
    (10306, 'COMPENSATORY', '调休',         6, 1, 1),
    (10307, 'OTHER',        '其他',         7, 1, 1);

INSERT IGNORE INTO biz_resign_reason (id, code, name, sort, status, built_in) VALUES
    (10401, 'CAREER',  '个人发展', 1, 1, 1),
    (10402, 'SALARY',  '薪酬原因', 2, 1, 1),
    (10403, 'FAMILY',  '家庭原因', 3, 1, 1),
    (10404, 'CONTRACT','合同到期', 4, 1, 1),
    (10405, 'DISMISS', '辞退',     5, 1, 1),
    (10406, 'OTHER',   '其他',     6, 1, 1);

INSERT IGNORE INTO biz_education (id, code, name, sort, status, built_in) VALUES
    (11001, 'primary',    '小学',      1, 1, 1),
    (11002, 'junior',     '初中',      2, 1, 1),
    (11003, 'senior',     '高中',      3, 1, 1),
    (11004, 'vocational', '中专/中技', 4, 1, 1),
    (11005, 'college',    '大专',      5, 1, 1),
    (11006, 'bachelor',   '本科',      6, 1, 1),
    (11007, 'master',     '硕士',      7, 1, 1),
    (11008, 'doctor',     '博士',      8, 1, 1);

INSERT IGNORE INTO biz_bank (id, code, name, sort, status, built_in) VALUES
    (11101, 'icbc',  '工商银行', 1, 1, 1),
    (11102, 'abc',   '农业银行', 2, 1, 1),
    (11103, 'boc',   '中国银行', 3, 1, 1),
    (11104, 'ccb',   '建设银行', 4, 1, 1),
    (11105, 'bocom', '交通银行', 5, 1, 1),
    (11106, 'psbc',  '邮储银行', 6, 1, 1),
    (11107, 'cmb',   '招商银行', 7, 1, 1),
    (11108, 'citic', '中信银行', 8, 1, 1),
    (11109, 'spdb',  '浦发银行', 9, 1, 1),
    (11110, 'cmbc',  '民生银行', 10, 1, 1),
    (11111, 'cib',   '兴业银行', 11, 1, 1),
    (11112, 'cgb',   '广发银行', 12, 1, 1),
    (11113, 'pab',   '平安银行', 13, 1, 1),
    (11114, 'hxb',   '华夏银行', 14, 1, 1),
    (11115, 'ceb',   '光大银行', 15, 1, 1),
    (11116, 'czb',   '浙商银行', 16, 1, 1),
    (11117, 'bsb',   '渤海银行', 17, 1, 1);

INSERT IGNORE INTO biz_family_relation (id, code, name, sort, status, built_in) VALUES
    (11501, 'father',   '父亲', 1, 1, 1),
    (11502, 'mother',   '母亲', 2, 1, 1),
    (11503, 'spouse',   '配偶', 3, 1, 1),
    (11504, 'son',      '儿子', 4, 1, 1),
    (11505, 'daughter', '女儿', 5, 1, 1),
    (11506, 'brother',  '兄弟', 6, 1, 1),
    (11507, 'sister',   '姐妹', 7, 1, 1),
    (11508, 'other',    '其他', 8, 1, 1);

-- ============================================================
-- 审批中心：配置（cfg_approval_*）与运行（appr_*）同库同在 settings
-- 业务模块不存审批过程，只通过 /internal/approval/** 发起与接收回调。
-- ============================================================

CREATE TABLE IF NOT EXISTS cfg_approval_template (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    code        VARCHAR(64)  NOT NULL COMMENT '模板编码，如 ehr_onboard_default',
    name        VARCHAR(64)  NOT NULL COMMENT '模板名称',
    app_code    VARCHAR(32)  NOT NULL COMMENT '归属应用 ehr / cms / blog',
    biz_type    VARCHAR(64)  NOT NULL COMMENT '业务场景，如 ehr.onboard',
    biz_name    VARCHAR(64)  NOT NULL COMMENT '场景名快照',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    version     INT          NOT NULL DEFAULT 1 COMMENT '每次保存+1；实例按快照跑不受影响',
    remark      VARCHAR(255) DEFAULT '' COMMENT '备注',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appr_tpl_code (code, deleted),
    UNIQUE KEY uk_appr_tpl_biz (app_code, biz_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批模板';

CREATE TABLE IF NOT EXISTS cfg_approval_step (
    id              BIGINT       NOT NULL COMMENT '雪花ID',
    template_id     BIGINT       NOT NULL COMMENT '所属模板',
    seq             INT          NOT NULL COMMENT '步骤序号，从1连续',
    name            VARCHAR(64)  NOT NULL COMMENT '如「用人部门审批」',
    condition_expr  VARCHAR(500) DEFAULT '' COMMENT '步骤级条件，空=总执行；为假整步跳过',
    condition_desc  VARCHAR(255) DEFAULT '' COMMENT '条件的人读文案',
    reject_action   VARCHAR(16)  NOT NULL DEFAULT 'END' COMMENT 'END退回发起人 PREV退回上一步 NODE仅本步重走',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appr_step_seq (template_id, seq, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批步骤（串行单元）';

CREATE TABLE IF NOT EXISTS cfg_approval_node (
    id              BIGINT       NOT NULL COMMENT '雪花ID',
    template_id     BIGINT       NOT NULL COMMENT '所属模板（冗余，免联表）',
    step_id         BIGINT       NOT NULL COMMENT '所属步骤',
    seq             INT          NOT NULL COMMENT '步骤内顺序',
    name            VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '分支名，留空用审批人摘要',
    condition_expr  VARCHAR(500) DEFAULT '' COMMENT '分支条件，空=总生效',
    condition_desc  VARCHAR(255) DEFAULT '',
    approvers       TEXT         NOT NULL COMMENT '审批人规则JSON数组：[{"type":"ORG_LEADER","levelOffset":0}]',
    approve_mode    VARCHAR(8)   NOT NULL DEFAULT 'ANY' COMMENT 'ANY或签(竞争) ALL会签(并行) SEQ依次',
    allow_self      TINYINT      NOT NULL DEFAULT 0 COMMENT '允许审批自己提交的单据',
    skip_same       TINYINT      NOT NULL DEFAULT 1 COMMENT '与上一步骤同人则跳过',
    on_empty        VARCHAR(8)   NOT NULL DEFAULT 'SKIP' COMMENT '无人可审：SKIP跳过 BLOCK阻塞',
    timeout_hours   INT          DEFAULT NULL COMMENT '预留：超时提醒',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appr_node_step (step_id, seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批分支（步骤内并行）';

CREATE TABLE IF NOT EXISTS cfg_biz_scene (
    id           BIGINT      NOT NULL COMMENT '雪花ID',
    app_code     VARCHAR(32) NOT NULL,
    biz_type     VARCHAR(64) NOT NULL COMMENT '如 ehr.leave',
    biz_name     VARCHAR(64) NOT NULL,
    form_schema  TEXT        COMMENT '条件变量字典JSON：条件表达式可引用的字段',
    field_schema TEXT        COMMENT '页面字段池JSON：可展示字段（FORM/ENTITY/DERIVED/SYSTEM）',
    table_bindings TEXT       COMMENT '数据表绑定JSON：{tables:[{table,fk,label,main,mode}]}，mode=MAIN_ROW/BIZ_ROW/FK_LIST',
    subject_field VARCHAR(64) DEFAULT NULL COMMENT 'formData 里业务主体ID的键名：employeeId / orgId / articleId',
    biz_id_kind  VARCHAR(16)  DEFAULT NULL COMMENT 'SUBJECT=bizId 是业务主体 / DOC=bizId 是本次单据',
    perm_hint    VARCHAR(64) DEFAULT '' COMMENT '发起权限提示',
    source       VARCHAR(16) NOT NULL DEFAULT 'REGISTER' COMMENT 'REGISTER业务注册 MANUAL手工',
    sort         INT         NOT NULL DEFAULT 0,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appr_scene (app_code, biz_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批业务场景';

CREATE TABLE IF NOT EXISTS cfg_approval_page (
    id            BIGINT      NOT NULL COMMENT '雪花ID',
    template_id   BIGINT      NOT NULL,
    view          VARCHAR(16) NOT NULL DEFAULT 'APPROVAL' COMMENT 'APPROVAL审批人 DETAIL发起人回看 AUDIT审计',
    render_mode   VARCHAR(16) NOT NULL DEFAULT 'SECTIONS' COMMENT 'GRID/SECTIONS/TEMPLATE/COMPONENT/ROUTE',
    config        MEDIUMTEXT  COMMENT '字段与布局JSON',
    html_tpl      MEDIUMTEXT  COMMENT 'render_mode=TEMPLATE 的模板正文',
    component_key VARCHAR(64) DEFAULT '' COMMENT 'render_mode=COMPONENT 的前端组件key',
    route_path    VARCHAR(255) DEFAULT '' COMMENT 'render_mode=ROUTE 的自定义页面地址（应用内路由或完整URL）',
    version       INT         NOT NULL DEFAULT 1,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_appr_page_view (template_id, view, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批页面配置';

-- 「自定义页面路由」渲染模式：存量库补列（幂等）。
-- ROUTE 模式不逐字段配置，审批抽屉信息区直接 iframe 嵌入这个地址，流程与操作区保持平台渲染。
SET @rp_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'cfg_approval_page' AND column_name = 'route_path');
SET @rp_sql = IF(@rp_col = 0,
    "ALTER TABLE cfg_approval_page ADD COLUMN route_path VARCHAR(255) DEFAULT '' COMMENT 'render_mode=ROUTE 的自定义页面地址（应用内路由或完整URL）' AFTER component_key",
    'SELECT 1');
PREPARE rp_stmt FROM @rp_sql; EXECUTE rp_stmt; DEALLOCATE PREPARE rp_stmt;

CREATE TABLE IF NOT EXISTS appr_instance (
    id               BIGINT       NOT NULL COMMENT '雪花ID',
    template_id      BIGINT       NOT NULL,
    template_code    VARCHAR(64)  NOT NULL COMMENT '模板编码快照',
    template_version INT          NOT NULL DEFAULT 1,
    biz_type         VARCHAR(64)  NOT NULL,
    biz_name         VARCHAR(64)  NOT NULL,
    biz_id           VARCHAR(64)  NOT NULL COMMENT '本次业务单据主键（可能是数字或字符串）',
    subject_id       VARCHAR(64)  DEFAULT NULL COMMENT '业务主体ID（员工/组织/文章），与 biz_id 显式解耦',
    biz_id_kind      VARCHAR(16)  DEFAULT NULL COMMENT 'SUBJECT=业务主体 / DOC=本次单据；NULL=存量未标注',
    biz_no           VARCHAR(64)  DEFAULT '' COMMENT '业务方自己的业务单号（调用方传入）',
    appr_no          VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '审批单自身的单号（按单号规则生成）',
    title            VARCHAR(200) NOT NULL DEFAULT '' COMMENT '如「入职审批 · 张伟」',
    applicant_id     BIGINT       NOT NULL COMMENT '发起人账号ID',
    applicant_name   VARCHAR(64)  DEFAULT '',
    applicant_org_id BIGINT       DEFAULT NULL,
    org_path         VARCHAR(255) DEFAULT '' COMMENT '发起时组织路径快照',
    form_data        MEDIUMTEXT   COMMENT '业务表单快照JSON（提交时冻结）',
    snapshot         MEDIUMTEXT   COMMENT '流程快照JSON：步骤+分支+解析后的审批人',
    table_bindings   MEDIUMTEXT   COMMENT '数据表绑定快照JSON（发起时冻结，改场景绑定不影响在途单）',
    page_version     INT          DEFAULT NULL COMMENT '页面配置锁定版本，空=跟随最新',
    status           TINYINT      NOT NULL DEFAULT 0 COMMENT '0进行中 1已通过 2已驳回 3已撤回 4已终止',
    current_step     INT          NOT NULL DEFAULT 1,
    total_step       INT          NOT NULL DEFAULT 1,
    callback_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '业务回调：0不需要 1待回调 2成功 3失败',
    callback_url     VARCHAR(255) DEFAULT '' COMMENT '终审回调地址（业务方提供）',
    finished_at      DATETIME     DEFAULT NULL,
    deleted          TINYINT      NOT NULL DEFAULT 0,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appr_inst_biz (biz_type, biz_id, status),
    KEY idx_appr_inst_subject (biz_type, subject_id, status),
    KEY idx_appr_inst_applicant (applicant_id, created_at),
    KEY idx_appr_inst_status (status, current_step)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批实例（在途单据）';

-- 业务单号：存量库补列（幂等）。biz_no 是调用方自己的业务单据编号。
SET @bn_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'appr_instance' AND column_name = 'biz_no');
SET @bn_sql = IF(@bn_col = 0,
    "ALTER TABLE appr_instance ADD COLUMN biz_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务方自己的业务单号（调用方传入）' AFTER biz_id_kind",
    'SELECT 1');
PREPARE bn_stmt FROM @bn_sql; EXECUTE bn_stmt; DEALLOCATE PREPARE bn_stmt;

-- 审批单号：存量库补列（幂等）。审批引擎发起时按「单号规则」中 appr_instance 的规则生成。
SET @an_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'appr_instance' AND column_name = 'appr_no');
SET @an_sql = IF(@an_col = 0,
    "ALTER TABLE appr_instance ADD COLUMN appr_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '审批单自身的单号' AFTER biz_no",
    'SELECT 1');
PREPARE an_stmt FROM @an_sql; EXECUTE an_stmt; DEALLOCATE PREPARE an_stmt;

-- 数据表绑定快照：存量库补列。发起审批时把场景当时的绑定冻结进来，
-- 之后管理端改场景绑定只影响新发起的单，在途单继续按旧绑定渲染（用户 2026-11-06 要求）。
SET @ti_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'appr_instance' AND column_name = 'table_bindings');
SET @ti_sql = IF(@ti_col = 0,
    "ALTER TABLE appr_instance ADD COLUMN table_bindings MEDIUMTEXT COMMENT '数据表绑定快照JSON（发起时冻结）' AFTER snapshot",
    'SELECT 1');
PREPARE ti_stmt FROM @ti_sql;
EXECUTE ti_stmt;
DEALLOCATE PREPARE ti_stmt;
-- 补列前已在途的单没有快照，用场景当前绑定回填一次（只填空值，幂等）
UPDATE appr_instance i
    JOIN cfg_biz_scene s ON s.biz_type = i.biz_type AND s.deleted = 0
SET i.table_bindings = s.table_bindings
WHERE i.table_bindings IS NULL
  AND i.status = 0
  AND s.table_bindings IS NOT NULL;

-- 「业务主体 / 本次单据」双键：存量库补列（幂等）。
-- subject_id 让列表反查与回调定位不再靠 formData + Java 白名单猜 bizId 语义；
-- biz_id_kind 区分 SUBJECT（bizId 就是员工/组织）与 DOC（bizId 是申请单/记录行）。
SET @si_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'appr_instance' AND column_name = 'subject_id');
SET @si_sql = IF(@si_col = 0,
    "ALTER TABLE appr_instance ADD COLUMN subject_id VARCHAR(64) NULL COMMENT '业务主体ID' AFTER biz_id",
    'SELECT 1');
PREPARE si_stmt FROM @si_sql; EXECUTE si_stmt; DEALLOCATE PREPARE si_stmt;

SET @bk_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'appr_instance' AND column_name = 'biz_id_kind');
SET @bk_sql = IF(@bk_col = 0,
    "ALTER TABLE appr_instance ADD COLUMN biz_id_kind VARCHAR(16) NULL COMMENT 'SUBJECT / DOC' AFTER subject_id",
    'SELECT 1');
PREPARE bk_stmt FROM @bk_sql; EXECUTE bk_stmt; DEALLOCATE PREPARE bk_stmt;

SET @six_col = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = 'settings' AND table_name = 'appr_instance' AND index_name = 'idx_appr_inst_subject');
SET @six_sql = IF(@six_col = 0,
    'ALTER TABLE appr_instance ADD INDEX idx_appr_inst_subject (biz_type, subject_id, status)',
    'SELECT 1');
PREPARE six_stmt FROM @six_sql; EXECUTE six_stmt; DEALLOCATE PREPARE six_stmt;

SET @sf_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'cfg_biz_scene' AND column_name = 'subject_field');
SET @sf_sql = IF(@sf_col = 0,
    "ALTER TABLE cfg_biz_scene ADD COLUMN subject_field VARCHAR(64) NULL COMMENT 'formData 主体键'",
    'SELECT 1');
PREPARE sf_stmt FROM @sf_sql; EXECUTE sf_stmt; DEALLOCATE PREPARE sf_stmt;

SET @sk_col = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = 'settings' AND table_name = 'cfg_biz_scene' AND column_name = 'biz_id_kind');
SET @sk_sql = IF(@sk_col = 0,
    "ALTER TABLE cfg_biz_scene ADD COLUMN biz_id_kind VARCHAR(16) NULL COMMENT 'SUBJECT / DOC'",
    'SELECT 1');
PREPARE sk_stmt FROM @sk_sql; EXECUTE sk_stmt; DEALLOCATE PREPARE sk_stmt;

-- 存量实例回填：主体 ID 按场景 subject_field 从冻结表单取；主体与 bizId 相同即老口径 SUBJECT
UPDATE appr_instance i
    JOIN cfg_biz_scene s ON s.biz_type = i.biz_type AND s.deleted = 0
SET i.subject_id = COALESCE(
        JSON_UNQUOTE(JSON_EXTRACT(i.form_data, CONCAT('$.', IFNULL(s.subject_field, 'employeeId')))),
        i.biz_id),
    i.biz_id_kind = IF(i.subject_id = i.biz_id, 'SUBJECT', IFNULL(s.biz_id_kind, 'SUBJECT'))
WHERE i.subject_id IS NULL;

CREATE TABLE IF NOT EXISTS appr_task (
    id                 BIGINT       NOT NULL COMMENT '雪花ID',
    instance_id        BIGINT       NOT NULL,
    step_seq           INT          NOT NULL,
    step_name          VARCHAR(64)  DEFAULT '',
    node_id            BIGINT       DEFAULT NULL COMMENT '分支节点ID（快照里的）',
    node_seq           INT          DEFAULT 0,
    node_name          VARCHAR(64)  DEFAULT '',
    approver_id        BIGINT       NOT NULL COMMENT '审批人账号ID',
    approver_name      VARCHAR(64)  DEFAULT '',
    approver_source    VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER直接 DELEGATE代收',
    delegate_owner_id  BIGINT       DEFAULT NULL COMMENT '代收时原审批人',
    delegate_owner_name VARCHAR(64) DEFAULT '',
    task_kind          VARCHAR(8)   NOT NULL DEFAULT 'APPROVE' COMMENT 'APPROVE审批 CC抄送',
    status             TINYINT      NOT NULL DEFAULT 0 COMMENT '0待办 1通过 2驳回 3跳过 4作废 9已转办',
    approve_mode       VARCHAR(8)   NOT NULL DEFAULT 'ANY',
    seq_in_node        INT          NOT NULL DEFAULT 0 COMMENT '会签/依次模式下节点内序号',
    opinion            VARCHAR(500) DEFAULT '' COMMENT '审批意见',
    acted_at           DATETIME     DEFAULT NULL,
    due_at             DATETIME     DEFAULT NULL COMMENT '预留：超时时间',
    deleted            TINYINT      NOT NULL DEFAULT 0,
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appr_task_inst (instance_id, step_seq),
    KEY idx_appr_task_approver (approver_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务（每人每条）';

CREATE TABLE IF NOT EXISTS appr_action (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    instance_id BIGINT       NOT NULL,
    task_id     BIGINT       DEFAULT NULL,
    step_seq    INT          DEFAULT 0,
    node_id     BIGINT       DEFAULT NULL,
    node_name   VARCHAR(64)  DEFAULT '',
    actor_id    BIGINT       DEFAULT NULL COMMENT '操作人账号ID',
    actor_name  VARCHAR(64)  DEFAULT '',
    action      VARCHAR(16)  NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/SKIP/TRANSFER/ADD_SIGN/CC/WITHDRAW/TERMINATE',
    target_id   BIGINT       DEFAULT NULL COMMENT '转办/加签目标',
    target_name VARCHAR(64)  DEFAULT '',
    opinion     VARCHAR(500) DEFAULT '',
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appr_act_inst (instance_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流水';

CREATE TABLE IF NOT EXISTS appr_delegate (
    id            BIGINT       NOT NULL COMMENT '雪花ID',
    owner_id      BIGINT       NOT NULL COMMENT '委托人账号ID',
    owner_name    VARCHAR(64)  DEFAULT '',
    delegate_id   BIGINT       NOT NULL COMMENT '被委托人账号ID',
    delegate_name VARCHAR(64)  DEFAULT '',
    biz_type      VARCHAR(64)  DEFAULT '' COMMENT '空=全部业务',
    start_time    DATETIME     NOT NULL,
    end_time      DATETIME     NOT NULL,
    enabled       TINYINT      NOT NULL DEFAULT 1,
    remark        VARCHAR(255) DEFAULT '',
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appr_dele_owner (owner_id, enabled),
    KEY idx_appr_dele_target (delegate_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批委托（一段时间全量代收）';

CREATE TABLE IF NOT EXISTS appr_callback (
    id            BIGINT       NOT NULL COMMENT '雪花ID',
    instance_id   BIGINT       NOT NULL,
    biz_type      VARCHAR(64)  NOT NULL,
    biz_id        VARCHAR(64)  NOT NULL,
    result        TINYINT      NOT NULL COMMENT '1通过 2驳回 3撤回 4终止',
    callback_url  VARCHAR(255) NOT NULL,
    payload       MEDIUMTEXT   COMMENT '回调报文快照',
    status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待回调 1成功 2失败',
    retry_count   INT          NOT NULL DEFAULT 0,
    next_retry_at DATETIME     DEFAULT NULL,
    last_error    VARCHAR(500) DEFAULT '',
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_appr_cb_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务回调补偿（终审结果异步通知业务方）';

-- ------------------------------------------------------------
-- ehr.emp_update 人员信息变更：审批对比页切 ROUTE 模式（幂等）。
-- 对比页是 ehr-web 的自定义路由页面，按审批快照渲染「原数据删除线 / 新数据红色」的变更差异，
-- 审批抽屉的信息区直接 iframe 嵌入这个地址，流程与操作区保持平台渲染。
-- 已有 APPROVAL 页面配置且未切 ROUTE 的，只切模式与默认地址（页面里改过的地址不会被覆盖）；
-- 还没有页面配置的，按模板补一行。地址随环境不同可在「设置 → 审批配置」里改（这里写本地开发默认值）。
-- ------------------------------------------------------------
UPDATE cfg_approval_page p
JOIN cfg_approval_template t ON p.template_id = t.id
SET p.render_mode = 'ROUTE',
    p.route_path = 'http://localhost:5179/approval/emp-change'
WHERE t.app_code = 'ehr' AND t.biz_type = 'ehr.emp_update'
  AND p.view = 'APPROVAL' AND p.deleted = 0
  AND (p.render_mode <> 'ROUTE' OR p.route_path IS NULL OR p.route_path = '');

INSERT INTO cfg_approval_page (id, template_id, view, render_mode, config, route_path, version, deleted)
SELECT 202611070000000001, t.id, 'APPROVAL', 'ROUTE', '{}', 'http://localhost:5179/approval/emp-change', 1, 0
FROM cfg_approval_template t
WHERE t.app_code = 'ehr' AND t.biz_type = 'ehr.emp_update' AND t.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM cfg_approval_page p
                  WHERE p.template_id = t.id AND p.view = 'APPROVAL' AND p.deleted = 0);

-- ============================================================
-- 待办 / 消息：配置（cfg_notice_rule）与消息箱（notice_message）
-- 站内方式只有「web 待办」和「web 消息」两种且互斥，邮件可与任意一种叠加。
-- 其他业务通过 /internal/notice/send 按事件编码发送，配置由运营中心维护。
-- ============================================================

CREATE TABLE IF NOT EXISTS cfg_notice_rule (
    id          BIGINT       NOT NULL COMMENT '雪花ID',
    code        VARCHAR(64)  NOT NULL COMMENT '事件编码，如 approval.approved',
    app_code    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '归属应用编码，空=通用；配置树按「应用→模块」分组',
    module      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '模块，如「审批中心」',
    name        VARCHAR(64)  NOT NULL COMMENT '配置描述，如「审批通过通知发起人」',
    title_tpl   VARCHAR(200) NOT NULL DEFAULT '' COMMENT '标题模板，{变量} 由发送方传入',
    content_tpl VARCHAR(500) NOT NULL DEFAULT '' COMMENT '内容模板',
    channel     TINYINT      NOT NULL DEFAULT 2 COMMENT '站内方式：1待办 2消息（二选一）',
    email       TINYINT      NOT NULL DEFAULT 0 COMMENT '1同时发邮件 0不发',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    built_in    TINYINT      NOT NULL DEFAULT 0 COMMENT '内置规则不可删除',
    sort        INT          NOT NULL DEFAULT 0,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_notice_rule_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='待办/消息推送配置';

CREATE TABLE IF NOT EXISTS notice_message (
    id         BIGINT        NOT NULL COMMENT '雪花ID',
    account_id BIGINT        NOT NULL COMMENT '接收人账号ID',
    rule_code  VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '触发规则编码',
    kind       TINYINT       NOT NULL DEFAULT 2 COMMENT '1待办 2消息',
    module     VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '模块',
    title      VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '渲染后的标题',
    content    VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '渲染后的内容',
    biz_type   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '业务类型，便于跳转',
    biz_id     VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '业务主键',
    biz_no     VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '业务编码，如审批单号；纯展示',
    applicant_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '发起人：待办为申请人，审批结果消息为实际操作人',
    link       VARCHAR(255)  NOT NULL DEFAULT '' COMMENT '点击跳转的前端路由',
    read_at    DATETIME      DEFAULT NULL COMMENT '已读时间，NULL=未读',
    deleted    TINYINT       NOT NULL DEFAULT 0,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_notice_acc (account_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内待办/消息';

-- 旧库补列：消息列表「发起人」（待办=申请人，审批结果消息=实际通过/驳回的人）
SET @an_col = (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = 'settings' AND table_name = 'notice_message' AND column_name = 'applicant_name');
SET @an_sql = IF(@an_col = 0,
    "ALTER TABLE notice_message ADD COLUMN applicant_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '发起人：待办为申请人，审批结果消息为实际操作人' AFTER biz_no",
    'SELECT 1');
PREPARE an_stmt FROM @an_sql; EXECUTE an_stmt; DEALLOCATE PREPARE an_stmt;

INSERT IGNORE INTO cfg_notice_rule
    (id, code, module, name, title_tpl, content_tpl, channel, email, enabled, built_in, sort) VALUES
    (11201, 'approval.task',     '审批中心', '有新审批待办时通知审批人',
     '待办提醒：{bizName}', '「{title}」等待你审批，发起人：{applicantName}。', 1, 0, 1, 1, 1),
    (11202, 'approval.approved', '审批中心', '审批通过通知发起人',
     '审批已通过：{bizName}', '你发起的「{title}」已审批通过。', 2, 0, 1, 1, 2),
    (11203, 'approval.rejected', '审批中心', '审批驳回通知发起人',
     '审批已驳回：{bizName}', '你发起的「{title}」已被驳回，驳回意见：{opinion}。', 2, 0, 1, 1, 3);

-- 离职/减员审批通过后的「关闭账号」待办：减员群发给发起人之外不群发；离职群发给 HR 部门全员。
-- bizType=ehr.close-account + bizId=员工ID：关闭账号成功后由 EHR 反调 /internal/notice/biz-handled 统一置读。
INSERT IGNORE INTO cfg_notice_rule
    (id, code, module, name, title_tpl, content_tpl, channel, email, enabled, built_in, sort) VALUES
    (11204, 'ehr.resign.close', '人力中心', '离职审批通过提醒 HR 关闭账号',
     '待办：关闭离职员工账号', '{employeeName}的离职审批已经通过，请及时关闭{employeeName}的账号！', 1, 0, 1, 1, 4),
    (11205, 'ehr.reduce.close', '人力中心', '减员审批通过提醒发起人关闭账号',
     '待办：关闭减员员工账号', '你发起的{employeeName}的减员审批已经通过，请及时关闭{employeeName}的账号！', 1, 0, 1, 1, 5);

-- ============================================================
-- 业务单号规则（cfg_code_rule）
-- 以「业务库表名」为业务标识，业务方生成数据前调 /internal/code-rules/next 取号。
-- 审批单号由审批引擎在发起时按 appr_instance 规则生成并落库。
-- 生成时用「读流水号 → 条件更新」的乐观并发，last_no / last_period 记录上次生成结果。
-- 分隔符列名用 separator_char，避开 MySQL 保留字 separator。
-- ============================================================

CREATE TABLE IF NOT EXISTS cfg_code_rule (
    id             BIGINT       NOT NULL COMMENT '雪花ID',
    app_code       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '归属应用编码，仅用于分组展示',
    table_name     VARCHAR(64)  NOT NULL COMMENT '业务库表名，如 appr_instance；取号时的业务标识',
    prefix         VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '单号前缀',
    separator_char VARCHAR(8)   NOT NULL DEFAULT '' COMMENT '各段之间的分隔符，如 -',
    seq_length     INT          NOT NULL DEFAULT 4 COMMENT '流水号位数',
    pad_zero       TINYINT      NOT NULL DEFAULT 1 COMMENT '1不足位数补0 0不补',
    reset_monthly  TINYINT      NOT NULL DEFAULT 0 COMMENT '1按月重置流水号 0不重置',
    start_no       INT          NOT NULL DEFAULT 1 COMMENT '起始流水号',
    date_format    VARCHAR(32)  NOT NULL DEFAULT 'yyyyMMdd' COMMENT '日期格式',
    expr_format    VARCHAR(64)  NOT NULL DEFAULT '{prefix}{date}{seq}' COMMENT '表达式格式，由前缀/分隔符固定拼出，前端只读',
    last_no        INT          NOT NULL DEFAULT 0 COMMENT '最近生成的流水号',
    last_period    VARCHAR(16)  NOT NULL DEFAULT '' COMMENT '最近生成周期 yyyyMM，用于按月重置判断',
    deleted        TINYINT      NOT NULL DEFAULT 0,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code_rule_target (table_name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务单号规则';

INSERT IGNORE INTO cfg_code_rule
    (id, app_code, table_name, prefix, separator_char, seq_length, pad_zero, reset_monthly, start_no, date_format, expr_format)
VALUES
    (11301, 'settings', 'appr_instance', 'SP', '', 4, 1, 0, 1, 'yyyyMMdd', '{prefix}{date}{seq}'),
    (11302, 'ehr', 'ehr_resign_apply', 'LEAVE', '-', 4, 1, 0, 1, 'yyyyMMdd', '{prefix}-{date}-{seq}'),
    (11303, 'ehr', 'ehr_regularize_apply', 'REG', '-', 4, 1, 0, 1, 'yyyyMMdd', '{prefix}-{date}-{seq}'),
    (11304, 'ehr', 'ehr_announcement', 'NOTICE', '-', 4, 1, 0, 1, 'yyyyMMdd', '{prefix}-{date}-{seq}');

