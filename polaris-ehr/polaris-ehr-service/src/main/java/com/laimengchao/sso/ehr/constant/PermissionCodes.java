package com.laimengchao.sso.ehr.constant;

/**
 * 权限码常量。**必须与统一账号中心 sso_permission 表里的 code 完全一致**，
 * 前端路由 meta.perm、菜单显隐、后端 @Permit 三处都读这里的同一份字符串。
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    /* 菜单 */
    public static final String ORG_MENU = "org";
    public static final String EMPLOYEE_MENU = "employee";
    public static final String LEAVE_MENU = "leave";
    public static final String APPROVAL_MENU = "approval";
    /** 通知管理（人力中心维护内部通知，账号中心「通知中心」展示） */
    public static final String ANNOUNCEMENT_MENU = "announcement";

    /* 组织架构 */
    public static final String ORG_VIEW = "org:view";
    public static final String ORG_MANAGE = "org:manage";

    /* 人员 */
    public static final String EMPLOYEE_VIEW = "employee:view";
    public static final String EMPLOYEE_CREATE = "employee:create";
    public static final String EMPLOYEE_EDIT = "employee:edit";
    public static final String EMPLOYEE_DELETE = "employee:delete";
    public static final String EMPLOYEE_ACCOUNT = "employee:account";
    public static final String EMPLOYEE_RESIGN = "employee:resign";
    /** 批量导入员工（上传预览 → 确认入库） */
    public static final String EMPLOYEE_IMPORT = "employee:import";

    /* 招聘 */
    /** 查看招聘候选人看板与 Offer 列表 */
    public static final String RECRUIT_VIEW = "recruit:view";
    /** 维护候选人、发起/撤销录用 Offer、审批通过后转建档 */
    public static final String RECRUIT_MANAGE = "recruit:manage";

    /* 请假 */
    public static final String LEAVE_VIEW = "leave:view";
    public static final String LEAVE_APPLY = "leave:apply";
    public static final String LEAVE_APPROVE = "leave:approve";
    /** 查看团队请假视图（按组织/数据范围） */
    public static final String LEAVE_VIEW_TEAM = "leave:view:team";
    /** 查看指定员工的假期额度与流水 */
    public static final String LEAVE_QUOTA_VIEW = "leave:quota:view";
    /** 发起额度调整审批 */
    public static final String LEAVE_QUOTA_ADJUST = "leave:quota:adjust";
    /** 初始化 / 直接维护员工额度 */
    public static final String LEAVE_QUOTA_MANAGE = "leave:quota:manage";
    /** 维护请假类型规则（半天/工作日/附件/天数上限） */
    public static final String LEAVE_RULE_MANAGE = "leave:rule:manage";

    /* 合同与档案附件 */
    /** 查看合同列表 / 合同详情 / 合同附件 */
    public static final String CONTRACT_VIEW = "contract:view";
    /** 新增 / 编辑 / 续签 / 终止合同，上传与删除档案附件 */
    public static final String CONTRACT_MANAGE = "contract:manage";

    /* 轻量考勤（P1-2） */
    /** 查看加班/调休与月度考勤汇总（含本人提交加班） */
    public static final String OVERTIME_VIEW = "overtime:view";
    /** 加班终审 / 月度汇总确认 / 团队考勤查看 */
    public static final String OVERTIME_APPROVE = "overtime:approve";

    /* 人员数据范围（决定列表/详情能看到哪些人的数据） */
    /** 全部人员 */
    public static final String EMPLOYEE_DATA_ALL = "employee:data:all";
    /** 本组织及下级 */
    public static final String EMPLOYEE_DATA_ORG_TREE = "employee:data:org_tree";
    /** 仅本组织 */
    public static final String EMPLOYEE_DATA_ORG = "employee:data:org";

    /* 人员敏感数据与审计 */
    /** 查看手机号 / 身份证 / 银行卡明文 */
    public static final String EMPLOYEE_SENSITIVE_VIEW = "employee:sensitive:view";
    /** 导出人员数据 */
    public static final String EMPLOYEE_EXPORT = "employee:export";
    /** 查看敏感操作审计日志 */
    public static final String AUDIT_VIEW = "audit:view";

    /* 审批运营 */
    public static final String APPROVAL_OPS = "approval:ops";

    /* 离职 / 减员 / 审批中心 */
    /** 员工自助提交离职申请 */
    public static final String RESIGN_APPLY = "resign:apply";
    /** 员工自助提交转正申请 */
    public static final String REGULARIZE_APPLY = "regularize:apply";
    public static final String APPROVAL_VIEW = "approval:view";
    /** 审批请假 / 离职 / 减员（部门负责人） */
    public static final String APPROVAL_APPROVE = "approval:approve";

    /* 通知管理 */
    /** 新建 / 编辑 / 发布 / 下架 / 删除内部通知 */
    public static final String ANNOUNCEMENT_MANAGE = "announcement:manage";
}
