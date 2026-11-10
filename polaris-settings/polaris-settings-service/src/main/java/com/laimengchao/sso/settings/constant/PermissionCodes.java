package com.laimengchao.sso.settings.constant;

/**
 * 权限码常量。**必须与统一账号中心 sso_permission 表里的 code 完全一致**，
 * 前端路由 meta.perm、菜单显隐、后端 @Permit 三处都读这里的同一份字符串。
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    /* 菜单 */
    public static final String DICT_MENU = "dict";
    /** 主数据管理菜单（银行/学历/职位等业务主数据，独立于字典） */
    public static final String BIZ_MENU = "biz";
    /** 审批中心菜单 */
    public static final String APPROVAL_MENU = "approval";
    /** 安全配置菜单 */
    public static final String SECURITY_MENU = "security";
    /** 消息配置菜单 */
    public static final String NOTICE_MENU = "notice";
    /** 单号规则菜单 */
    public static final String CODE_MENU = "code";
    /** 埋点管理菜单 */
    public static final String EMBED_MENU = "embed";

    /* 埋点配置 */
    /** 查看埋点配置与历史 */
    public static final String EMBED_VIEW = "embed:view";
    /** 编辑埋点配置 */
    public static final String EMBED_MANAGE = "embed:manage";

    /* 单号规则 */
    /** 查看业务单号规则 */
    public static final String CODE_VIEW = "code:view";
    /** 单号规则增删改 */
    public static final String CODE_MANAGE = "code:manage";

    /* 消息配置 */
    /** 查看待办/消息配置 */
    public static final String NOTICE_VIEW = "notice:view";
    /** 编辑待办/消息配置 */
    public static final String NOTICE_MANAGE = "notice:manage";

    /* 安全配置 */
    /** 查看安全配置（初始密码、密码规则、登录策略） */
    public static final String SECURITY_VIEW = "security:view";
    /** 保存安全配置 */
    public static final String SECURITY_MANAGE = "security:manage";

    /* 审批中心 */
    /** 查看审批模板与页面配置 */
    public static final String APPROVAL_VIEW = "approval:view";
    /** 模板、步骤、分支、页面的增删改 */
    public static final String APPROVAL_MANAGE = "approval:manage";

    /* 字典 */
    /** 查看字典类型与字典项 */
    public static final String DICT_VIEW = "dict:view";
    /** 字典类型增删改 */
    public static final String DICT_TYPE_MANAGE = "dict:type:manage";
    /** 字典项增删改 */
    public static final String DICT_ITEM_MANAGE = "dict:item:manage";

    /* 业务主数据（真表：biz_bank / biz_education / biz_position / biz_leave_type / biz_resign_reason / biz_family_relation） */
    /** 查看主数据 */
    public static final String BIZ_VIEW = "biz:view";
    /** 主数据增删改 */
    public static final String BIZ_MANAGE = "biz:manage";
}
