package com.laimengchao.sso.common;

/**
 * 错误码契约：common 的 {@link ErrorCode}（通用段）与各服务自己的业务码枚举
 * （如 EhrErrorCode 7xxx / SettingsErrorCode 8xxx）都实现本接口，
 * {@link R}、{@link BizException}、{@link GlobalExceptionHandler} 只认契约不认具体枚举。
 */
public interface IErrorCode {

    int getCode();

    String getMessage();

    /**
     * 业务码 → HTTP 状态码覆盖。0 表示走默认规则（400/401/403/404/409 同步，
     * 其余 200）；个别业务码需要特定 HTTP 状态时在枚举常量上覆盖，
     * 如 BlogErrorCode 的 DATA_NOT_FOUND→404、CANNOT_DELETE_PUBLISHED_ARTICLE→409。
     */
    default int httpStatus() {
        return 0;
    }
}
