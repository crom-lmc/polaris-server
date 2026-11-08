package com.laimengchao.sso.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 令牌下发结果。前端只需保留 accessToken（放内存）+ refreshToken（放安全存储）。
 */
@Data
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    /** 固定 Bearer，前端拼 Authorization 头用 */
    private String tokenType;
    /** access token 剩余有效秒数 */
    private long expiresIn;
    private AuthUser user;
}
