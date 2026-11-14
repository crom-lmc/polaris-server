package com.laimengchao.sso.common.security;

/**
 * 全局唯一的权限码常量。
 *
 * <p>**必须与统一账号中心 sso_permission 表里的 code 完全一致**。
 * 前端路由 meta.perm、菜单显隐、后端 @Permit 三处都读这里的同一份字符串。
 * 新增 / 改名 / 删除权限码时，先改这里 → 再改 data.sql 种子 → 最后在 DB 执行。
 *
 * <p>按 app_code 分组，每组上方注释标明归属应用。
 * 常量命名风格：{GROUP}_{ACTION}，全大写 + 下划线。
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    /* ============================================================
     * 应用编码（sso_application.code）
     * ============================================================ */
    /** 平台自身（账号/角色/权限/应用管理） */
    public static final String APP_SSO = "sso";
    public static final String APP_EHR = "ehr";
    public static final String APP_JOB = "job";
    public static final String APP_SETTINGS = "settings";
    public static final String APP_BLOG = "blog";
    public static final String APP_CMS = "cms";

    /* ============================================================
     * SSO 账号中心（app_code = sso）
     * ============================================================ */
    /* 菜单 */
    public static final String SSO_MENU_DASHBOARD = "dashboard";
    public static final String SSO_MENU_ACCOUNT = "account";
    public static final String SSO_MENU_ROLE = "role";
    public static final String SSO_MENU_PERMISSION = "permission";
    public static final String SSO_MENU_APP = "app";
    public static final String SSO_MENU_LOG = "log";
    public static final String SSO_MENU_NOTICE = "notice";

    /* 账户按钮 */
    public static final String ACCOUNT_LIST = "account:list";
    public static final String ACCOUNT_LOG_VIEW = "account:log:view";
    public static final String ACCOUNT_CREATE = "account:create";
    public static final String ACCOUNT_EDIT = "account:edit";
    public static final String ACCOUNT_DELETE = "account:delete";
    public static final String ACCOUNT_ENABLE = "account:enable";
    public static final String ACCOUNT_DISABLE = "account:disable";
    public static final String ACCOUNT_UNLOCK = "account:unlock";
    public static final String ACCOUNT_RESET_PWD = "account:resetPassword";
    public static final String ACCOUNT_APP_ASSIGN = "account:app:assign";

    /* 角色按钮 */
    public static final String ROLE_LIST = "role:list";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_EDIT = "role:edit";
    public static final String ROLE_DELETE = "role:delete";
    public static final String ROLE_ASSIGN = "role:assign";

    /* 权限点按钮 */
    public static final String PERMISSION_LIST = "permission:list";
    public static final String PERMISSION_CREATE = "permission:create";
    public static final String PERMISSION_EDIT = "permission:edit";
    public static final String PERMISSION_DELETE = "permission:delete";

    /* 应用注册按钮 */
    public static final String APP_LIST = "app:list";
    public static final String APP_CREATE = "app:create";
    public static final String APP_EDIT = "app:edit";
    public static final String APP_DELETE = "app:delete";
    public static final String APP_SECRET_RESET = "app:secret:reset";

    /* 通知中心（账号中心展示人力中心发布的内部通知） */
    public static final String NOTICE_VIEW = "notice:view";

    /* ============================================================
     * EHR 人力中心（app_code = ehr）
     * ============================================================ */
    /* 菜单 */
    public static final String ORG_MENU = "org";
    public static final String EMPLOYEE_MENU = "employee";
    public static final String LEAVE_MENU = "leave";
    public static final String APPROVAL_MENU = "approval";
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
    public static final String EMPLOYEE_IMPORT = "employee:import";
    public static final String EMPLOYEE_EXPORT = "employee:export";

    /* 招聘 */
    public static final String RECRUIT_VIEW = "recruit:view";
    public static final String RECRUIT_MANAGE = "recruit:manage";

    /* 请假 */
    public static final String LEAVE_VIEW = "leave:view";
    public static final String LEAVE_APPLY = "leave:apply";
    public static final String LEAVE_APPROVE = "leave:approve";
    public static final String LEAVE_VIEW_TEAM = "leave:view:team";
    public static final String LEAVE_QUOTA_VIEW = "leave:quota:view";
    public static final String LEAVE_QUOTA_ADJUST = "leave:quota:adjust";
    public static final String LEAVE_QUOTA_MANAGE = "leave:quota:manage";
    public static final String LEAVE_RULE_MANAGE = "leave:rule:manage";

    /* 合同 */
    public static final String CONTRACT_VIEW = "contract:view";
    public static final String CONTRACT_MANAGE = "contract:manage";

    /* 加班 */
    public static final String OVERTIME_VIEW = "overtime:view";
    public static final String OVERTIME_APPROVE = "overtime:approve";

    /* 考勤 */
    public static final String ATTENDANCE_VIEW = "attendance:view";
    public static final String ATTENDANCE_MANAGE = "attendance:manage";
    public static final String ATTENDANCE_MONTH_UNLOCK = "attendance:month:unlock";

    /* 人员数据范围 */
    public static final String EMPLOYEE_DATA_ALL = "employee:data:all";
    public static final String EMPLOYEE_DATA_ORG_TREE = "employee:data:org_tree";
    public static final String EMPLOYEE_DATA_ORG = "employee:data:org";
    public static final String EMPLOYEE_SENSITIVE_VIEW = "employee:sensitive:view";

    /* 审计日志 */
    public static final String AUDIT_VIEW = "audit:view";

    /* 审批运营 */
    public static final String APPROVAL_OPS = "approval:ops";

    /* 离职/转正自助 */
    public static final String RESIGN_APPLY = "resign:apply";
    public static final String REGULARIZE_APPLY = "regularize:apply";

    /* 审批通用 */
    public static final String APPROVAL_VIEW = "approval:view";
    public static final String APPROVAL_APPROVE = "approval:approve";

    /* 通知管理（人力中心） */
    public static final String ANNOUNCEMENT_MANAGE = "announcement:manage";

    /* ============================================================
     * JOB 调度中心（app_code = job）
     * ============================================================ */
    public static final String JOB_MENU = "job";
    public static final String JOB_VIEW = "job:view";
    public static final String JOB_MANAGE = "job:manage";
    public static final String JOB_RUN = "job:run";
    public static final String LOG_VIEW = "log:view";

    /* ============================================================
     * SETTINGS 配置中心（app_code = settings）
     * ============================================================ */
    /* 菜单 */
    public static final String DICT_MENU = "dict";
    public static final String BIZ_MENU = "biz";
    /* APPROVAL_MENU 已在 EHR 组定义 */
    public static final String SECURITY_MENU = "security";
    public static final String NOTICE_MENU = "notice";
    public static final String CODE_MENU = "code";
    public static final String EMBED_MENU = "embed";

    /* 埋点 */
    public static final String EMBED_VIEW = "embed:view";
    public static final String EMBED_MANAGE = "embed:manage";

    /* 单号规则 */
    public static final String CODE_VIEW = "code:view";
    public static final String CODE_MANAGE = "code:manage";

    /* 消息配置（NOTICE_VIEW 已在 SSO 组定义，值相同） */
    public static final String NOTICE_MANAGE = "notice:manage";

    /* 安全配置 */
    public static final String SECURITY_VIEW = "security:view";
    public static final String SECURITY_MANAGE = "security:manage";

    /* 审批模板 */
    /* APPROVAL_VIEW / APPROVAL_MANAGE 已在 EHR 组定义（字符串相同 "approval:view"/"approval:manage"） */
    public static final String APPROVAL_MANAGE = "approval:manage";

    /* 字典 */
    public static final String DICT_VIEW = "dict:view";
    public static final String DICT_TYPE_MANAGE = "dict:type:manage";
    public static final String DICT_ITEM_MANAGE = "dict:item:manage";

    /* 业务主数据 */
    public static final String BIZ_VIEW = "biz:view";
    public static final String BIZ_MANAGE = "biz:manage";

    /* ============================================================
     * BLOG 博客（app_code = blog）
     * ============================================================ */
    /* 菜单 */
    public static final String BLOG_DASHBOARD_MENU = "dashboard";
    public static final String BLOG_ARTICLE_MENU = "article";
    public static final String BLOG_CATEGORY_MENU = "category";
    public static final String BLOG_TAG_MENU = "tag";
    public static final String BLOG_COMMENT_MENU = "comment";
    public static final String BLOG_MEDIA_MENU = "media";
    public static final String BLOG_THEME_MENU = "theme";
    public static final String BLOG_SETTING_MENU = "setting";
    public static final String BLOG_LOG_MENU = "log";

    /* 文章 */
    public static final String ARTICLE_LIST = "article:list";
    public static final String ARTICLE_CREATE = "article:create";
    public static final String ARTICLE_EDIT = "article:edit";
    public static final String ARTICLE_PUBLISH = "article:publish";
    public static final String ARTICLE_DELETE = "article:delete";

    /* 分类 */
    public static final String CATEGORY_LIST = "category:list";
    public static final String CATEGORY_EDIT = "category:edit";
    public static final String CATEGORY_DELETE = "category:delete";

    /* 标签 */
    public static final String TAG_LIST = "tag:list";
    public static final String TAG_EDIT = "tag:edit";
    public static final String TAG_DELETE = "tag:delete";

    /* 评论 */
    public static final String COMMENT_LIST = "comment:list";
    public static final String COMMENT_AUDIT = "comment:audit";
    public static final String COMMENT_DELETE = "comment:delete";
    public static final String COMMENT_REPLY = "comment:reply";

    /* 媒体库 */
    public static final String MEDIA_LIST = "media:list";
    public static final String MEDIA_EDIT = "media:edit";
    public static final String MEDIA_DELETE = "media:delete";
    public static final String MEDIA_UPLOAD = "media:upload";

    /* 主题 */
    public static final String THEME_LIST = "theme:list";
    public static final String THEME_EDIT = "theme:edit";
    public static final String THEME_DELETE = "theme:delete";
    public static final String THEME_PUBLISH = "theme:publish";

    /* 站点设置 */
    public static final String SETTING_VIEW = "setting:view";
    public static final String SETTING_EDIT = "setting:edit";

    /* 操作日志 */
    public static final String LOG_LIST = "log:list";
    public static final String LOG_CLEAN = "log:clean";

    /* 统计 */
    public static final String STAT_VIEW = "stat:view";

    /* ============================================================
     * CMS 内容中心（app_code = cms）
     * ============================================================ */
    public static final String DASHBOARD_VIEW = "dashboard:view";

    /* 文章 */
    /* ARTICLE_CREATE / ARTICLE_EDIT / ARTICLE_DELETE 已在 BLOG 组定义（字符串相同） */
    public static final String ARTICLE_VIEW = "article:view";
    public static final String ARTICLE_AUDIT = "article:audit";
    public static final String ARTICLE_PIN = "article:pin";
    public static final String ARTICLE_ROLLBACK = "article:rollback";
    public static final String ARTICLE_VERSION = "article:version";

    /* 分类 */
    /* CATEGORY_LIST 已在 BLOG 组定义 */
    public static final String CATEGORY_VIEW = "category:view";
    public static final String CATEGORY_MANAGE = "category:manage";

    /* 标签 */
    /* TAG_LIST 已在 BLOG 组定义 */
    public static final String TAG_VIEW = "tag:view";
    public static final String TAG_MANAGE = "tag:manage";

    /* 媒体库 */
    /* MEDIA_LIST / MEDIA_DELETE / MEDIA_UPLOAD 已在 BLOG 组定义 */
    public static final String MEDIA_VIEW = "media:view";

    /* Banner */
    public static final String BANNER_VIEW = "banner:view";
    public static final String BANNER_MANAGE = "banner:manage";

    /* 友情链接 */
    public static final String LINK_VIEW = "link:view";
    public static final String LINK_MANAGE = "link:manage";

    /* 门户布局 */
    public static final String PORTAL_VIEW = "portal:view";
    public static final String PORTAL_CONFIG = "portal:config";
}
