package com.laimengchao.sso.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 服务间重置密码：调用方（sso-auth）已完成邮箱验证码校验。 */
@Data
public class InternalResetPasswordRequest {

    @NotNull(message = "账号ID不能为空")
    private Long accountId;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
