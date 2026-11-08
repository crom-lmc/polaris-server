package com.laimengchao.sso.common;

import lombok.Getter;

/**
 * 通用错误码（全服务共享的基础段）。各服务的业务码段（7xxx/8xxx 等）
 * 在各自服务里定义（如 EhrErrorCode），实现 {@link IErrorCode} 即可接入
 * 统一的 {@link R} / {@link BizException} / {@link GlobalExceptionHandler}。
 */
@Getter
public enum ErrorCode implements IErrorCode {

    OK(0, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限执行该操作"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方式不支持"),
    CONFLICT(409, "数据冲突（如用户名/邮箱已存在）"),
    TOO_MANY_REQUESTS(429, "操作过于频繁，请稍后再试"),
    BIZ_ERROR(500, "业务处理失败"),
    SYSTEM_ERROR(5000, "系统异常");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
