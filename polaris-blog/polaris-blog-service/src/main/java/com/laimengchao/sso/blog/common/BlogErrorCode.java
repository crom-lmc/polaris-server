package com.laimengchao.sso.blog.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Blog 业务错误码段（通用段见 common 的 ErrorCode；本枚举只含 BLOG 业务码）。
 * 实现 IErrorCode 即可接入统一的 R / BizException / GlobalExceptionHandler。
 */
@Getter
@AllArgsConstructor
public enum BlogErrorCode implements IErrorCode {

    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "用户名或密码错误"),
    ACCOUNT_DISABLED(1003, "账号已停用"),
    ACCOUNT_LOCKED(1004, "登录失败次数过多，账号已被锁定，请稍后再试"),
    CAPTCHA_ERROR(1005, "验证码错误或已过期"),
    TOKEN_INVALID(1006, "令牌无效或已过期"),
    USERNAME_EXISTS(1007, "用户名已存在"),
    OLD_PASSWORD_ERROR(1008, "原密码错误"),
    DATA_NOT_FOUND(2001, "数据不存在") {
        @Override public int httpStatus() { return 404; }
    },
    DATA_ALREADY_EXISTS(2002, "数据已存在") {
        @Override public int httpStatus() { return 409; }
    },
    SLUG_DUPLICATE(2003, "别名已存在，请更换") {
        @Override public int httpStatus() { return 409; }
    },
    FILE_TYPE_NOT_ALLOWED(2004, "文件类型不允许"),
    FILE_TOO_LARGE(2005, "文件超过大小限制"),
    UPLOAD_FAILED(2006, "文件上传失败"),
    CANNOT_DELETE_ACTIVE_THEME(2007, "不能删除正在使用的主题"),
    CANNOT_DELETE_DEFAULT_THEME(2008, "默认主题不支持删除"),
    CATEGORY_IN_USE(2009, "该分类已被文章引用，不能删除") {
        @Override public int httpStatus() { return 409; }
    },
    TAG_IN_USE(2010, "该标签已被文章引用，不能删除") {
        @Override public int httpStatus() { return 409; }
    },
    CANNOT_DELETE_PUBLISHED_ARTICLE(2011, "已发布的文章不允许删除，请先下线") {
        @Override public int httpStatus() { return 409; }
    },
    COMMENT_CLOSED(3001, "该文章已关闭评论"),
    COMMENT_TOO_FREQUENT(3002, "评论过于频繁，请稍后再试"),
    COMMENT_CONTAINS_SENSITIVE(3003, "评论包含敏感词"),
    ARTICLE_NOT_PUBLISHED(3004, "文章未发布"),
    THEME_CONFIG_INVALID(4001, "主题配置不合法"),
    THEME_CSS_UNSAFE(4002, "自定义 CSS 包含不允许的内容");

    private final int code;
    private final String message;
}
