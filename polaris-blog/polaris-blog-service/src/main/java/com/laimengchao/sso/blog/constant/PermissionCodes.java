package com.laimengchao.sso.blog.constant;

/**
 * 权限码常量。**必须与统一账号中心 sso_permission 表里的 code 完全一致**，
 * 前端路由 meta.perm、菜单显隐、后端 @Permit 三处都读这里的同一份字符串。
 */
public final class PermissionCodes {

    private PermissionCodes() {
    }

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
}
