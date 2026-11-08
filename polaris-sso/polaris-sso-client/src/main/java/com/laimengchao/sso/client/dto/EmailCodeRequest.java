package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 邮箱验证码生成请求（对应 sso-account 的 /internal/accounts/email/code）。
 */
@Data
public class EmailCodeRequest {

    private String email;
    private String scene;
    private String ip;
}
