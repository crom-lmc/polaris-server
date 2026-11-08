package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 登录请求（模板自包含的 session 登录；生产由认证服务/SAS 替代）。
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}
