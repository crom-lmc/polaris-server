package com.laimengchao.sso.job.constant;

/**
 * 权限码常量。**必须与统一账号中心 sso_permission 表里的 code 完全一致**，
 * 前端路由 meta.perm、菜单显隐、后端 @Permit 三处都读这里的同一份字符串。
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    /** 调度中心菜单 */
    public static final String JOB_MENU = "job";
    /** 查看任务、日志、执行器、报警配置 */
    public static final String JOB_VIEW = "job:view";
    /** 任务/报警的增删改、暂停恢复 */
    public static final String JOB_MANAGE = "job:manage";
    /** 手动执行一次 */
    public static final String JOB_RUN = "job:run";
}
