package com.laimengchao.sso.client.exception;

/**
 * sso-auth 的 OAuth2 token 端点用裸 JSON 返回错误（{@code {error, error_description}}），
 * 不是统一 R 壳。Feign 默认会把 4xx 抛成 FeignException 并丢掉协议语义，
 * 这里把错误体解析出来，调用方据此原样透传 error_description。
 */
public class AuthOAuthException extends RuntimeException {

    private final int status;
    private final String error;
    private final String errorDescription;

    public AuthOAuthException(int status, String error, String errorDescription) {
        super(errorDescription != null && !errorDescription.isBlank() ? errorDescription
                : error != null && !error.isBlank() ? error : "oauth error");
        this.status = status;
        this.error = error;
        this.errorDescription = errorDescription;
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    /** 对外提示：优先 error_description，其次 error */
    public String displayMessage() {
        if (errorDescription != null && !errorDescription.isBlank()) {
            return errorDescription;
        }
        if (error != null && !error.isBlank()) {
            return error;
        }
        return "认证失败，请稍后重试";
    }
}
