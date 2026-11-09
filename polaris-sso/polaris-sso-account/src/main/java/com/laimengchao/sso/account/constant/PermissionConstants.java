package com.laimengchao.sso.account.constant;

import com.laimengchao.sso.common.enums.PermissionType;

/**
 * 按钮级权限码常量 + 内置权限清单（供 BootstrapInitializer 初始化）。
 *
 * <p>约定：权限码 = 资源:操作。前端拿到当前账户的权限码集合后，按码控制按钮显隐。
 * 例如拥有 account:create 才渲染「新建账户」按钮，否则该按钮隐藏且后端接口返回 403。
 *
 * <p>目前权限控制聚焦 sso-account 模块；后续接入网关/认证模块时，按相同约定扩展即可。
 */
public final class PermissionConstants {

    /**
     * 平台自身的应用编码。内置权限码、{@code @Permit} 注解、管理台页面全部归属 {@code sso}；
     * 业务应用（博客、钓鱼后台…）用自己的 {@code sso_application.code} 作为 appCode。
     */
    public static final String PLATFORM_APP = "sso";

    /** 菜单（页面级）：与前端左侧菜单一一对应 */
    /** 概览（默认首页，登录即可见，不做权限显隐，仅作为权限目录节点） */
    public static final String MENU_DASHBOARD = "dashboard";
    public static final String MENU_ACCOUNT = "account";
    public static final String MENU_ROLE = "role";
    public static final String MENU_PERMISSION = "permission";
    public static final String MENU_APP = "app";
    /** 登录日志（独立菜单，查看权限挂在其下） */
    public static final String MENU_LOG = "log";
    /** 通知中心（统一账号中心展示人力中心发布的内部通知，员工基本功能） */
    public static final String MENU_NOTICE = "notice";

    /** 账户模块按钮级权限 */
    public static final String ACCOUNT_LIST = "account:list";
    /** 登录日志查看（审计） */
    public static final String ACCOUNT_LOG_VIEW = "account:log:view";
    public static final String ACCOUNT_CREATE = "account:create";
    public static final String ACCOUNT_EDIT = "account:edit";
    public static final String ACCOUNT_DELETE = "account:delete";
    public static final String ACCOUNT_ENABLE = "account:enable";
    public static final String ACCOUNT_DISABLE = "account:disable";
    public static final String ACCOUNT_UNLOCK = "account:unlock";
    public static final String ACCOUNT_RESET_PWD = "account:resetPassword";

    /** 角色模块按钮级权限 */
    public static final String ROLE_LIST = "role:list";
    public static final String ROLE_CREATE = "role:create";
    public static final String ROLE_EDIT = "role:edit";
    public static final String ROLE_DELETE = "role:delete";
    public static final String ROLE_ASSIGN = "role:assign";

    /** 权限模块（权限点本身的维护） */
    public static final String PERMISSION_LIST = "permission:list";
    public static final String PERMISSION_CREATE = "permission:create";
    public static final String PERMISSION_EDIT = "permission:edit";
    public static final String PERMISSION_DELETE = "permission:delete";

    /** 应用注册模块（SSO 保护的下游系统） */
    public static final String APP_LIST = "app:list";
    public static final String APP_CREATE = "app:create";
    public static final String APP_EDIT = "app:edit";
    public static final String APP_DELETE = "app:delete";
    /** 重置应用密钥：属高危操作，单独拆一个码，避免有编辑权的人随手换掉密钥 */
    public static final String APP_SECRET_RESET = "app:secret:reset";
    /** 给账号分配可访问应用 */
    public static final String ACCOUNT_APP_ASSIGN = "account:app:assign";

    /* ---------- 业务应用 ehr（人力中心）的内置权限 ---------- */

    /** 业务应用编码（sso_application.code） */
    public static final String EHR_APP = "ehr";
    public static final String EHR_MENU_ANNOUNCEMENT = "announcement";
    public static final String EHR_ANNOUNCEMENT_MANAGE = "announcement:manage";
    /** 审批运营：时效统计、积压看板、催办、导出（HR / 超管） */
    public static final String APPROVAL_OPS = "approval:ops";

    /** 通知中心按钮级权限 */
    public static final String NOTICE_VIEW = "notice:view";

    /** 内置权限定义（菜单 + 按钮）。parentCode 为 null 表示顶级菜单。 */
    public record PermDef(String name, String code, PermissionType type, String parentCode, int sort) {
    }

    public static final java.util.List<PermDef> BUILTIN = java.util.List.of(
            // —— 菜单 ——
            new PermDef("概览", MENU_DASHBOARD, PermissionType.MENU, null, 0),
            new PermDef("账户管理", MENU_ACCOUNT, PermissionType.MENU, null, 1),
            new PermDef("角色管理", MENU_ROLE, PermissionType.MENU, null, 2),
            new PermDef("权限管理", MENU_PERMISSION, PermissionType.MENU, null, 3),
            new PermDef("应用管理", MENU_APP, PermissionType.MENU, null, 4),
            new PermDef("登录日志", MENU_LOG, PermissionType.MENU, null, 5),
            new PermDef("通知中心", MENU_NOTICE, PermissionType.MENU, null, 6),
            // —— 通知中心按钮 ——
            new PermDef("查看通知", NOTICE_VIEW, PermissionType.BUTTON, MENU_NOTICE, 1),
            // —— 账户按钮 ——
            new PermDef("查询账户", ACCOUNT_LIST, PermissionType.BUTTON, MENU_ACCOUNT, 1),
            new PermDef("查看登录日志", ACCOUNT_LOG_VIEW, PermissionType.BUTTON, MENU_LOG, 1),
            new PermDef("新建账户", ACCOUNT_CREATE, PermissionType.BUTTON, MENU_ACCOUNT, 2),
            new PermDef("编辑账户", ACCOUNT_EDIT, PermissionType.BUTTON, MENU_ACCOUNT, 3),
            new PermDef("删除账户", ACCOUNT_DELETE, PermissionType.BUTTON, MENU_ACCOUNT, 4),
            new PermDef("启用账户", ACCOUNT_ENABLE, PermissionType.BUTTON, MENU_ACCOUNT, 5),
            new PermDef("禁用账户", ACCOUNT_DISABLE, PermissionType.BUTTON, MENU_ACCOUNT, 6),
            new PermDef("解锁账户", ACCOUNT_UNLOCK, PermissionType.BUTTON, MENU_ACCOUNT, 7),
            new PermDef("重置密码", ACCOUNT_RESET_PWD, PermissionType.BUTTON, MENU_ACCOUNT, 8),
            new PermDef("分配应用", ACCOUNT_APP_ASSIGN, PermissionType.BUTTON, MENU_ACCOUNT, 10),
            // —— 角色按钮 ——
            new PermDef("查询角色", ROLE_LIST, PermissionType.BUTTON, MENU_ROLE, 1),
            new PermDef("新建角色", ROLE_CREATE, PermissionType.BUTTON, MENU_ROLE, 2),
            new PermDef("编辑角色", ROLE_EDIT, PermissionType.BUTTON, MENU_ROLE, 3),
            new PermDef("删除角色", ROLE_DELETE, PermissionType.BUTTON, MENU_ROLE, 4),
            new PermDef("分配权限", ROLE_ASSIGN, PermissionType.BUTTON, MENU_ROLE, 5),
            // —— 权限按钮 ——
            new PermDef("查看权限", PERMISSION_LIST, PermissionType.BUTTON, MENU_PERMISSION, 1),
            new PermDef("新建权限", PERMISSION_CREATE, PermissionType.BUTTON, MENU_PERMISSION, 2),
            new PermDef("编辑权限", PERMISSION_EDIT, PermissionType.BUTTON, MENU_PERMISSION, 3),
            new PermDef("删除权限", PERMISSION_DELETE, PermissionType.BUTTON, MENU_PERMISSION, 4),
            // —— 应用按钮 ——
            new PermDef("查询应用", APP_LIST, PermissionType.BUTTON, MENU_APP, 1),
            new PermDef("新建应用", APP_CREATE, PermissionType.BUTTON, MENU_APP, 2),
            new PermDef("编辑应用", APP_EDIT, PermissionType.BUTTON, MENU_APP, 3),
            new PermDef("删除应用", APP_DELETE, PermissionType.BUTTON, MENU_APP, 4),
            new PermDef("重置应用密钥", APP_SECRET_RESET, PermissionType.BUTTON, MENU_APP, 5)
    );

    private PermissionConstants() {
    }

    /**
     * 业务应用 ehr（人力中心）的内置权限。
     * <p>
     * 与 {@link #BUILTIN} 分开是因为 sso_permission 按 app_code 隔离：
     * 这些码只对 ehr 应用有意义，不能混进平台自身的权限清单。
     */
    public static final java.util.List<PermDef> EHR_BUILTIN = java.util.List.of(
            new PermDef("通知管理", EHR_MENU_ANNOUNCEMENT, PermissionType.MENU, null, 6),
            new PermDef("维护通知", EHR_ANNOUNCEMENT_MANAGE, PermissionType.BUTTON, EHR_MENU_ANNOUNCEMENT, 1)
    );

    /** 通知中心默认授权角色：员工自助 */
    public static final java.util.List<String> NOTICE_ROLE_CODES = java.util.List.of("EMPLOYEE_SELF");

    /** 通知维护默认授权角色：人力与运营相关（角色不存在则安全跳过） */
    public static final java.util.List<String> ANNOUNCEMENT_MANAGE_ROLE_CODES = java.util.List.of(
            "HR_SPECIALIST", "HR_RECRUITER", "HR_COMP_BENEFIT", "HR_ER",
            "CONTENT_OPS", "CMS_OPS_MANAGER", "OPS_READONLY");

    /** 内置权限码集合（code -> true），用于「内置权限不可删」的判定。 */
    private static final java.util.Set<String> BUILTIN_CODES =
            BUILTIN.stream().map(PermDef::code).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * 是否内置权限码。
     *
     * <p>内置码被 {@code @Permit} 直接引用，删掉会让对应接口永久 403 且无法从界面恢复，
     * 因此禁止删除（可以改名，但不能删）。
     */
    public static boolean isBuiltin(String code) {
        return code != null && BUILTIN_CODES.contains(code);
    }

    /**
     * 是否内置权限码（带应用维度）。
     *
     * <p>业务应用完全可以定义与内置码同名的权限（如博客也有 {@code account:list}），
     * 那不是内置码，可以自由删除。只有 appCode 为 {@code sso} 时才看内置清单。
     */
    public static boolean isBuiltin(String appCode, String code) {
        return PLATFORM_APP.equals(appCode) && isBuiltin(code);
    }
}
