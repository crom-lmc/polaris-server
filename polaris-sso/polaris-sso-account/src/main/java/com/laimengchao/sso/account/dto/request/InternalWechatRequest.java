package com.laimengchao.sso.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 服务间：按 openid 反查绑定账户。 */
@Data
public class InternalWechatRequest {

    @NotBlank(message = "openid 不能为空")
    private String openid;

    /** open=开放平台扫码 mp=小程序 */
    private String appType;
}
