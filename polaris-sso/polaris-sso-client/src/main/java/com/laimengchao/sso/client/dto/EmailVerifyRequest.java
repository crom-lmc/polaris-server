package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 邮箱验证码校验请求（对应 sso-account 的 /internal/accounts/email/verify）。
 */
@Data
public class EmailVerifyRequest {

    private String email;
    private String scene;
    private String code;
}
