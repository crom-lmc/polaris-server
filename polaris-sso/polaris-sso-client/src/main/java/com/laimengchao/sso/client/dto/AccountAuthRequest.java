package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 服务间凭据校验请求（对应 sso-account 的 /internal/accounts/authenticate）。
 */
@Data
public class AccountAuthRequest {

    private String username;
    private String password;
    private String clientIp;
    private String userAgent;
    private String loginType;
    /** 权限码所属应用；缺省 sso */
    private String appCode;
}
