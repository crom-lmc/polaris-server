package com.laimengchao.sso.client.dto;

import lombok.Data;

/** 服务间：邮箱验证码校验通过后重置账号密码（不校验原密码）。 */
@Data
public class PasswordResetRequest {
    private Long accountId;
    private String newPassword;
}
