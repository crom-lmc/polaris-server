package com.laimengchao.sso.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 目标应用编码（可选）。缺省 {@code sso}，即平台管理台自身的权限域。
     *
     * <p>业务应用若自建登录页、直接调本接口，应填自己的应用编码，
     * 这样签发的令牌里 {@code perms} 只会包含该应用的按钮码，不会夹带平台权限。
     */
    private String appCode;
}
