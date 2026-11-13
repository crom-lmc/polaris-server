package com.laimengchao.sso.client.dto;

import lombok.Data;

/** 服务间：邮箱验证码校验通过后重置账号密码（不校验原密码）。 */
@Data
public class PasswordResetRequest {
    private Long accountId;
    private String newPassword;
    /** 原始客户端信息仅用于审计，不能由 account 的内网请求地址代替。 */
    private String clientIp;
    private String userAgent;
}
