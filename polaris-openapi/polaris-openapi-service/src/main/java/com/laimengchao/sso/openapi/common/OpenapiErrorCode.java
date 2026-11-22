package com.laimengchao.sso.openapi.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 接口开放平台业务错误码段（9xxx）。 */
@Getter
@AllArgsConstructor
public enum OpenapiErrorCode implements IErrorCode {

    TOKEN_INVALID(9001, "访问令牌无效或已过期"),
    SIGN_INVALID(9002, "签名校验失败"),
    SIGN_EXPIRED(9003, "签名时间戳超出允许偏差"),
    REPLAY_DETECTED(9004, "检测到重放请求（nonce 重复）"),
    PERM_DENIED(9005, "应用无权调用该开放接口"),
    API_NOT_FOUND(9006, "开放接口不存在或已停用"),
    RATE_LIMITED(9007, "调用频率超出限制"),
    CHANNEL_ERROR(9008, "下游服务调用失败"),
    CONFIG_ERROR(9009, "开放平台配置错误"),
    /* P1 出站 */
    CHANNEL_NOT_FOUND(9010, "出站渠道不存在或已停用"),
    APP_NOT_ALLOWED(9011, "应用无权使用该出站渠道"),
    CREDENTIAL_MISSING(9012, "出站渠道未配置凭证"),
    SEND_FAILED(9013, "第三方出站调用失败"),
    CIRCUIT_OPEN(9014, "出站渠道熔断中，请稍后重试"),
    INTERNAL_TOKEN_INVALID(9015, "内部令牌无效"),
    IP_NOT_ALLOWED(9016, "来源 IP 不在白名单"),
    TIMEOUT(9017, "第三方渠道调用超时");

    private final int code;
    private final String message;

    @Override
    public int httpStatus() {
        return switch (this) {
            case TOKEN_INVALID, SIGN_INVALID, SIGN_EXPIRED -> 401;
            case REPLAY_DETECTED, PERM_DENIED, APP_NOT_ALLOWED, IP_NOT_ALLOWED -> 403;
            case API_NOT_FOUND, CHANNEL_NOT_FOUND -> 404;
            case RATE_LIMITED -> 429;
            case CHANNEL_ERROR, SEND_FAILED -> 502;
            case CIRCUIT_OPEN -> 503;
            case TIMEOUT -> 504;
            default -> 0;
        };
    }
}
