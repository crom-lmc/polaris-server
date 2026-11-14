package com.laimengchao.sso.cms.constant;

/**
 * 权限码常量。**必须与统一账号中心 sso_permission 表里的 code 完全一致**，
 * 前端路由 meta.perm、菜单显隐、后端 @Permit 三处都读这里的同一份字符串。
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

    /* 仪表盘 */
    public static final String DASHBOARD_VIEW = "dashboard:view";

    /* 文章 */
    public static final String ARTICLE_VIEW = "article:view";
    public static final String ARTICLE_CREATE = "article:create";
    public static final String ARTICLE_EDIT = "article:edit";
    public static final String ARTICLE_AUDIT = "article:audit";
    public static final String ARTICLE_PUBLISH = "article:publish";
    public static final String ARTICLE_PIN = "article:pin";
    public static final String ARTICLE_DELETE = "article:delete";
    public static final String ARTICLE_VERSION = "article:version";
    public static final String ARTICLE_ROLLBACK = "article:rollback";

    /* 分类 */
    public static final String CATEGORY_VIEW = "category:view";
    public static final String CATEGORY_MANAGE = "category:manage";

    /* 标签 */
    public static final String TAG_VIEW = "tag:view";
    public static final String TAG_MANAGE = "tag:manage";

    /* 媒体库 */
    public static final String MEDIA_VIEW = "media:view";
    public static final String MEDIA_UPLOAD = "media:upload";
    public static final String MEDIA_DELETE = "media:delete";

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
