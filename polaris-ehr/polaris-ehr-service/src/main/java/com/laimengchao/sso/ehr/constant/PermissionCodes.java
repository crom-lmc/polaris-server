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

    /* 请假 */
    public static final String LEAVE_VIEW = "leave:view";
    public static final String LEAVE_APPLY = "leave:apply";
    public static final String LEAVE_APPROVE = "leave:approve";

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
