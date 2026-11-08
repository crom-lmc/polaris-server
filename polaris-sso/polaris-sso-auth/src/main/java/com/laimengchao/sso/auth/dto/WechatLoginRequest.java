package com.laimengchao.sso.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 微信扫码登录：携带开放平台回调拿到的 code。 */
@Data
public class WechatLoginRequest {

    @NotBlank(message = "code 不能为空")
    private String code;
}
