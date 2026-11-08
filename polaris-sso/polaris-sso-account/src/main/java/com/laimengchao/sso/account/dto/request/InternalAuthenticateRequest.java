package com.laimengchao.sso.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 服务间凭据校验请求（仅 sso-auth 调用）。 */
@Data
public class InternalAuthenticateRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 客户端 IP（由 auth 从请求中取，用于登录审计） */
    private String clientIp;
    /** User-Agent（用于登录审计） */
    private String userAgent;
    /** 登录方式：password / email / wechat */
    private String loginType;

    /**
     * 目标应用编码：决定返回的权限码属于哪个应用域。
     * 缺省 sso（平台管理台）；业务应用直接调用登录接口时填自己的 code。
     */
    private String appCode;
}
