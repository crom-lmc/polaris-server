package com.laimengchao.sso.portal.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Portal 业务错误码段（通用段见 common 的 ErrorCode；本枚举只含 PORTAL 业务码）。
 * 实现 IErrorCode 即可接入统一的 R / BizException / GlobalExceptionHandler。
 */
@Getter
@AllArgsConstructor
public enum PortalErrorCode implements IErrorCode {

    DATA_NOT_FOUND(7001, "数据不存在");

    private final int code;
    private final String message;
}
