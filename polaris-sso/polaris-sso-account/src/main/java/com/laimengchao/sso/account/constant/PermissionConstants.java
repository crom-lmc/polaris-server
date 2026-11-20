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
    public static final String MENU_MY_APPS = "my-apps";
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
            new PermDef("我的应用", MENU_MY_APPS, PermissionType.MENU, null, 1),
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

    /* ---------- 业务应用 forum（社区论坛）的内置权限 ---------- */

    public static final String FORUM_APP = "forum";
    public static final String FORUM_ADMIN_APP = "forum-admin";
    /** 论坛管理总开关（forum-admin 应用域），保留给管理端权限树做分组与「论坛管理员」角色标识 */
    public static final String FORUM_MENU_ADMIN = "forum:admin";
    /* —— 业务菜单（与论坛管理端侧栏一一对应） —— */
    public static final String FORUM_CONTENT_MANAGE = "forum:content";
    public static final String FORUM_COMMENT_MANAGE = "forum:comment";
    public static final String FORUM_REPORT_MANAGE = "forum:report";
    public static final String FORUM_TAXONOMY_MANAGE = "forum:taxonomy";
    public static final String FORUM_SITE_MANAGE = "forum:site";
    public static final String FORUM_SENSITIVE_MANAGE = "forum:sensitive";
    public static final String FORUM_EXCHANGE_MANAGE = "forum:exchange";
    public static final String FORUM_BADGE_MANAGE = "forum:badge";
    public static final String FORUM_LOTTERY_MANAGE = "forum:lottery";
    /* —— 内容管理按钮 —— */
    public static final String FORUM_CONTENT_VIEW = "forum:content:view";
    public static final String FORUM_CONTENT_PIN = "forum:content:pin";
    public static final String FORUM_CONTENT_RECOMMEND = "forum:content:recommend";
    public static final String FORUM_CONTENT_SOLVE = "forum:content:solve";
    public static final String FORUM_CONTENT_DELETE = "forum:content:delete";
    /* —— 评论管理按钮 —— */
    public static final String FORUM_COMMENT_VIEW = "forum:comment:view";
    public static final String FORUM_COMMENT_DELETE = "forum:comment:delete";
    /* —— 举报管理按钮 —— */
    public static final String FORUM_REPORT_VIEW = "forum:report:view";
    public static final String FORUM_REPORT_PROCESS = "forum:report:process";
    /* —— 板块与标签按钮 —— */
    public static final String FORUM_TAXONOMY_VIEW = "forum:taxonomy:view";
    public static final String FORUM_TAXONOMY_BOARD_MANAGE = "forum:taxonomy:board:manage";
    public static final String FORUM_TAXONOMY_TAG_MANAGE = "forum:taxonomy:tag:manage";
    /* —— 站点管理按钮 —— */
    public static final String FORUM_SITE_VIEW = "forum:site:view";
    public static final String FORUM_SITE_EDIT = "forum:site:edit";
    public static final String FORUM_SITE_LINK_MANAGE = "forum:site:link:manage";
    /* —— 敏感词管理按钮 —— */
    public static final String FORUM_SENSITIVE_VIEW = "forum:sensitive:view";
    public static final String FORUM_SENSITIVE_ADD = "forum:sensitive:add";
    public static final String FORUM_SENSITIVE_TOGGLE = "forum:sensitive:toggle";
    public static final String FORUM_SENSITIVE_DELETE = "forum:sensitive:delete";
    /* —— 礼品发放按钮 —— */
    public static final String FORUM_EXCHANGE_VIEW = "forum:exchange:view";
    public static final String FORUM_EXCHANGE_SHIP = "forum:exchange:ship";
    public static final String FORUM_EXCHANGE_ISSUE = "forum:exchange:issue";
    /* —— 勋章管理按钮 —— */
    public static final String FORUM_BADGE_VIEW = "forum:badge:view";
    public static final String FORUM_BADGE_CREATE = "forum:badge:create";
    public static final String FORUM_BADGE_EDIT = "forum:badge:edit";
    public static final String FORUM_BADGE_PUBLISH = "forum:badge:publish";
    public static final String FORUM_BADGE_OFFLINE = "forum:badge:offline";
    /* —— 抽奖配置按钮 —— */
    public static final String FORUM_LOTTERY_CONFIG_VIEW = "forum:lottery:config:view";
    public static final String FORUM_LOTTERY_CONFIG_EDIT = "forum:lottery:config:edit";

    /**
     * 论坛管理端（forum-admin / 北极星核）的内置权限：8 个业务菜单 + 27 个按钮。
     * 后台控制器 {@code @Permit} 直接引用这些码，删掉会让接口永久 403。
     */
    public static final java.util.List<PermDef> FORUM_ADMIN_BUILTIN = java.util.List.of(
            // —— 总开关与业务菜单 ——
            new PermDef("论坛管理", FORUM_MENU_ADMIN, PermissionType.MENU, null, 0),
            new PermDef("内容管理", FORUM_CONTENT_MANAGE, PermissionType.MENU, null, 1),
            new PermDef("评论管理", FORUM_COMMENT_MANAGE, PermissionType.MENU, null, 2),
            new PermDef("举报管理", FORUM_REPORT_MANAGE, PermissionType.MENU, null, 3),
            new PermDef("板块与标签", FORUM_TAXONOMY_MANAGE, PermissionType.MENU, null, 4),
            new PermDef("站点管理", FORUM_SITE_MANAGE, PermissionType.MENU, null, 5),
            new PermDef("敏感词管理", FORUM_SENSITIVE_MANAGE, PermissionType.MENU, null, 6),
            new PermDef("礼品发放", FORUM_EXCHANGE_MANAGE, PermissionType.MENU, null, 7),
            new PermDef("勋章管理", FORUM_BADGE_MANAGE, PermissionType.MENU, null, 8),
            new PermDef("抽奖配置", FORUM_LOTTERY_MANAGE, PermissionType.MENU, null, 9),
            // —— 内容管理按钮 ——
            new PermDef("查看内容", FORUM_CONTENT_VIEW, PermissionType.BUTTON, FORUM_CONTENT_MANAGE, 1),
            new PermDef("置顶/取消置顶", FORUM_CONTENT_PIN, PermissionType.BUTTON, FORUM_CONTENT_MANAGE, 2),
            new PermDef("推荐/取消推荐", FORUM_CONTENT_RECOMMEND, PermissionType.BUTTON, FORUM_CONTENT_MANAGE, 3),
            new PermDef("标记解决", FORUM_CONTENT_SOLVE, PermissionType.BUTTON, FORUM_CONTENT_MANAGE, 4),
            new PermDef("删除/恢复内容", FORUM_CONTENT_DELETE, PermissionType.BUTTON, FORUM_CONTENT_MANAGE, 5),
            // —— 评论管理按钮 ——
            new PermDef("查看评论", FORUM_COMMENT_VIEW, PermissionType.BUTTON, FORUM_COMMENT_MANAGE, 1),
            new PermDef("删除/恢复评论", FORUM_COMMENT_DELETE, PermissionType.BUTTON, FORUM_COMMENT_MANAGE, 2),
            // —— 举报管理按钮 ——
            new PermDef("查看举报", FORUM_REPORT_VIEW, PermissionType.BUTTON, FORUM_REPORT_MANAGE, 1),
            new PermDef("处理举报", FORUM_REPORT_PROCESS, PermissionType.BUTTON, FORUM_REPORT_MANAGE, 2),
            // —— 板块与标签按钮 ——
            new PermDef("查看板块与标签", FORUM_TAXONOMY_VIEW, PermissionType.BUTTON, FORUM_TAXONOMY_MANAGE, 1),
            new PermDef("板块增删改启停", FORUM_TAXONOMY_BOARD_MANAGE, PermissionType.BUTTON, FORUM_TAXONOMY_MANAGE, 2),
            new PermDef("标签增删改排序启停", FORUM_TAXONOMY_TAG_MANAGE, PermissionType.BUTTON, FORUM_TAXONOMY_MANAGE, 3),
            // —— 站点管理按钮 ——
            new PermDef("查看站点信息", FORUM_SITE_VIEW, PermissionType.BUTTON, FORUM_SITE_MANAGE, 1),
            new PermDef("保存站点信息", FORUM_SITE_EDIT, PermissionType.BUTTON, FORUM_SITE_MANAGE, 2),
            new PermDef("友链增删改启停", FORUM_SITE_LINK_MANAGE, PermissionType.BUTTON, FORUM_SITE_MANAGE, 3),
            // —— 敏感词管理按钮 ——
            new PermDef("查看敏感词", FORUM_SENSITIVE_VIEW, PermissionType.BUTTON, FORUM_SENSITIVE_MANAGE, 1),
            new PermDef("新增敏感词", FORUM_SENSITIVE_ADD, PermissionType.BUTTON, FORUM_SENSITIVE_MANAGE, 2),
            new PermDef("启用/停用敏感词", FORUM_SENSITIVE_TOGGLE, PermissionType.BUTTON, FORUM_SENSITIVE_MANAGE, 3),
            new PermDef("删除敏感词", FORUM_SENSITIVE_DELETE, PermissionType.BUTTON, FORUM_SENSITIVE_MANAGE, 4),
            // —— 礼品发放按钮 ——
            new PermDef("查看兑换记录", FORUM_EXCHANGE_VIEW, PermissionType.BUTTON, FORUM_EXCHANGE_MANAGE, 1),
            new PermDef("登记物流", FORUM_EXCHANGE_SHIP, PermissionType.BUTTON, FORUM_EXCHANGE_MANAGE, 2),
            new PermDef("确认签收", FORUM_EXCHANGE_ISSUE, PermissionType.BUTTON, FORUM_EXCHANGE_MANAGE, 3),
            // —— 勋章管理按钮 ——
            new PermDef("查看勋章", FORUM_BADGE_VIEW, PermissionType.BUTTON, FORUM_BADGE_MANAGE, 1),
            new PermDef("新建勋章", FORUM_BADGE_CREATE, PermissionType.BUTTON, FORUM_BADGE_MANAGE, 2),
            new PermDef("编辑勋章草稿", FORUM_BADGE_EDIT, PermissionType.BUTTON, FORUM_BADGE_MANAGE, 3),
            new PermDef("发布勋章", FORUM_BADGE_PUBLISH, PermissionType.BUTTON, FORUM_BADGE_MANAGE, 4),
            new PermDef("下线勋章", FORUM_BADGE_OFFLINE, PermissionType.BUTTON, FORUM_BADGE_MANAGE, 5),
            // —— 抽奖配置按钮 ——
            new PermDef("查看抽奖配置", FORUM_LOTTERY_CONFIG_VIEW, PermissionType.BUTTON, FORUM_LOTTERY_MANAGE, 1),
            new PermDef("维护抽奖配置", FORUM_LOTTERY_CONFIG_EDIT, PermissionType.BUTTON, FORUM_LOTTERY_MANAGE, 2)
    );

    /**
     * 社区论坛（forum / 北极星空）前台的内置权限：只控制需要登录才能操作的按钮。
     *
     * <p>前台没有后台菜单，浏览类接口（帖子、板块、评论、用户主页）完全公开，
     * 因此这里不设任何 {@code *:view} 码，只有写操作才需要判权。
     * 默认由 {@link #FORUM_MEMBER_ROLE_CODE} 角色全量授予，撤销即等价于限制该用户的社区互动。
     */
    public static final String FORUM_MENU_OPS = "forum:ops";
    public static final String FORUM_POST_CREATE = "forum:post:create";
    public static final String FORUM_COMMENT_CREATE = "forum:comment:create";
    public static final String FORUM_LIKE = "forum:like";
    public static final String FORUM_FAVORITE = "forum:favorite";
    public static final String FORUM_FOLLOW = "forum:follow";
    public static final String FORUM_REPORT_CREATE = "forum:report:create";
    public static final String FORUM_VOTE = "forum:vote";
    public static final String FORUM_DRAFT = "forum:draft";
    public static final String FORUM_ATTACHMENT_UPLOAD = "forum:attachment:upload";
    public static final String FORUM_PROFILE_EDIT = "forum:profile:edit";
    public static final String FORUM_CHECKIN = "forum:checkin";
    public static final String FORUM_LOTTERY_DRAW = "forum:lottery:draw";
    public static final String FORUM_GIFT_REDEEM = "forum:gift:redeem";

    public static final java.util.List<PermDef> FORUM_MEMBER_BUILTIN = java.util.List.of(
            // —— 分组菜单：权限管理里平铺展示，前台没有对应路由 ——
            new PermDef("社区操作", FORUM_MENU_OPS, PermissionType.MENU, null, 0),
            new PermDef("发布内容", FORUM_POST_CREATE, PermissionType.BUTTON, FORUM_MENU_OPS, 1),
            new PermDef("发表评论", FORUM_COMMENT_CREATE, PermissionType.BUTTON, FORUM_MENU_OPS, 2),
            new PermDef("点赞/取消点赞", FORUM_LIKE, PermissionType.BUTTON, FORUM_MENU_OPS, 3),
            new PermDef("收藏/取消收藏", FORUM_FAVORITE, PermissionType.BUTTON, FORUM_MENU_OPS, 4),
            new PermDef("关注/取消关注", FORUM_FOLLOW, PermissionType.BUTTON, FORUM_MENU_OPS, 5),
            new PermDef("举报内容", FORUM_REPORT_CREATE, PermissionType.BUTTON, FORUM_MENU_OPS, 6),
            new PermDef("参与投票", FORUM_VOTE, PermissionType.BUTTON, FORUM_MENU_OPS, 7),
            new PermDef("草稿箱", FORUM_DRAFT, PermissionType.BUTTON, FORUM_MENU_OPS, 8),
            new PermDef("上传附件", FORUM_ATTACHMENT_UPLOAD, PermissionType.BUTTON, FORUM_MENU_OPS, 9),
            new PermDef("编辑个人资料", FORUM_PROFILE_EDIT, PermissionType.BUTTON, FORUM_MENU_OPS, 10),
            new PermDef("每日签到", FORUM_CHECKIN, PermissionType.BUTTON, FORUM_MENU_OPS, 11),
            new PermDef("积分抽奖", FORUM_LOTTERY_DRAW, PermissionType.BUTTON, FORUM_MENU_OPS, 12),
            new PermDef("兑换礼品", FORUM_GIFT_REDEEM, PermissionType.BUTTON, FORUM_MENU_OPS, 13)
    );

    /** 前台操作权限码清单（授给论坛用户角色时用） */
    public static final java.util.List<String> FORUM_MEMBER_PERMISSIONS =
            FORUM_MEMBER_BUILTIN.stream().map(PermDef::code).toList();

    /** 论坛用户：拥有论坛访问权的账号默认绑定该角色，只含前台写操作权限 */
    public static final String FORUM_MEMBER_ROLE_CODE = "FORUM_MEMBER";

    public static final String FORUM_ADMIN_ROLE_CODE = "FORUM_ADMIN";
    /** 首次创建论坛管理员角色时，默认授权的来源角色 */
    public static final java.util.List<String> FORUM_ADMIN_SOURCE_ROLE_CODES = java.util.List.of(
            "CONTENT_OPS", "CMS_OPS_MANAGER");

    /* ---------- 论坛职能角色（运营中心按职能分工） ---------- */
    /** 论坛内容运营：内容管理 + 评论管理 + 敏感词管理 */
    public static final String FORUM_CONTENT_OPS_ROLE = "FORUM_CONTENT_OPS";
    /** 论坛社区管理：举报管理 + 板块与标签 + 站点管理 */
    public static final String FORUM_COMMUNITY_OPS_ROLE = "FORUM_COMMUNITY_OPS";
    /** 论坛活动运营：礼品发放 + 勋章管理 */
    public static final String FORUM_ACTIVITY_OPS_ROLE = "FORUM_ACTIVITY_OPS";

    /** 论坛职能角色 -> 名称与拥有的菜单/按钮权限码（初始化与手工核对共用） */
    public record ForumRoleDef(String code, String name, String remark, java.util.List<String> permissions) {
    }

    public static final java.util.List<ForumRoleDef> FORUM_OPERATION_ROLES = java.util.List.of(
            new ForumRoleDef(FORUM_CONTENT_OPS_ROLE, "论坛内容运营", "运营中心-内容运营：内容、评论与敏感词管理",
                    java.util.List.of(FORUM_CONTENT_MANAGE, FORUM_COMMENT_MANAGE, FORUM_SENSITIVE_MANAGE,
                            FORUM_CONTENT_VIEW, FORUM_CONTENT_PIN, FORUM_CONTENT_RECOMMEND, FORUM_CONTENT_SOLVE, FORUM_CONTENT_DELETE,
                            FORUM_COMMENT_VIEW, FORUM_COMMENT_DELETE,
                            FORUM_SENSITIVE_VIEW, FORUM_SENSITIVE_ADD, FORUM_SENSITIVE_TOGGLE, FORUM_SENSITIVE_DELETE)),
            new ForumRoleDef(FORUM_COMMUNITY_OPS_ROLE, "论坛社区管理", "运营中心-社区管理：举报、板块标签与站点管理",
                    java.util.List.of(FORUM_REPORT_MANAGE, FORUM_TAXONOMY_MANAGE, FORUM_SITE_MANAGE,
                            FORUM_REPORT_VIEW, FORUM_REPORT_PROCESS,
                            FORUM_TAXONOMY_VIEW, FORUM_TAXONOMY_BOARD_MANAGE, FORUM_TAXONOMY_TAG_MANAGE,
                            FORUM_SITE_VIEW, FORUM_SITE_EDIT, FORUM_SITE_LINK_MANAGE)),
            new ForumRoleDef(FORUM_ACTIVITY_OPS_ROLE, "论坛活动运营", "运营中心-活动运营：礼品发放、勋章与抽奖配置",
                    java.util.List.of(FORUM_EXCHANGE_MANAGE, FORUM_BADGE_MANAGE, FORUM_LOTTERY_MANAGE,
                            FORUM_EXCHANGE_VIEW, FORUM_EXCHANGE_SHIP, FORUM_EXCHANGE_ISSUE,
                            FORUM_BADGE_VIEW, FORUM_BADGE_CREATE, FORUM_BADGE_EDIT, FORUM_BADGE_PUBLISH, FORUM_BADGE_OFFLINE,
                            FORUM_LOTTERY_CONFIG_VIEW, FORUM_LOTTERY_CONFIG_EDIT))
    );

    /** 运营中心部门组织码 -> 该部门默认绑定的论坛职能角色（按部门职责分配） */
    public static final java.util.Map<String, java.util.List<String>> FORUM_ROLE_ORG_BINDINGS =
            java.util.Map.of(
                    "XC00010008", java.util.List.of(FORUM_CONTENT_OPS_ROLE, FORUM_COMMUNITY_OPS_ROLE, FORUM_ACTIVITY_OPS_ROLE),
                    "XC000100080001", java.util.List.of(FORUM_COMMUNITY_OPS_ROLE),
                    "XC000100080002", java.util.List.of(FORUM_COMMUNITY_OPS_ROLE, FORUM_ACTIVITY_OPS_ROLE),
                    "XC000100080003", java.util.List.of(FORUM_CONTENT_OPS_ROLE),
                    "XC000100080004", java.util.List.of(FORUM_ACTIVITY_OPS_ROLE)
            );

    /** 组织码为空的运营中心新员工 -> 论坛职能角色（按员工实际所属部门分配） */
    public static final java.util.Map<Long, java.util.List<String>> FORUM_ROLE_ACCOUNT_BINDINGS =
            java.util.Map.of(
                    2700000000000040061L, java.util.List.of(FORUM_COMMUNITY_OPS_ROLE),                       // 宁文韬-运营管理部
                    2700000000000040062L, java.util.List.of(FORUM_COMMUNITY_OPS_ROLE, FORUM_ACTIVITY_OPS_ROLE), // 蒲安琪-用户运营部
                    2700000000000040063L, java.util.List.of(FORUM_CONTENT_OPS_ROLE)                          // 赖瑞雪-内容运营部
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
