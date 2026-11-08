package com.laimengchao.sso.common;

import lombok.Getter;

/**
 * 业务异常。ControllerAdvice 统一转成 {@link R}。
 * 支持通用 {@link ErrorCode} 与各服务业务码（任何 {@link IErrorCode} 实现）；
 * 携带 IErrorCode 时，HTTP 状态可由其 {@link IErrorCode#httpStatus()} 覆盖。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    /** 抛出时使用的错误码枚举；用 (int, String) 构造时为 null */
    private final IErrorCode errorCode;

    public BizException(IErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(IErrorCode errorCode, String detail) {
        super(detail == null ? errorCode.getMessage() : detail);
        this.code = errorCode.getCode();
        this.errorCode = errorCode;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
        this.errorCode = null;
    }
}
