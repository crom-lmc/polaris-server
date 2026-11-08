package com.laimengchao.sso.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 服务间：校验邮箱验证码，返回可登录的账户ID。 */
@Data
public class InternalVerifyEmailRequest {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    private String scene;

    @NotBlank(message = "验证码不能为空")
    private String code;
}
