package com.laimengchao.sso.account.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 当前用户绑定微信。openid 由客户端经由微信 OAuth 流程获取后回传。 */
@Data
public class BindWechatRequest {

    @NotBlank(message = "openid 不能为空")
    private String openid;

    private String unionid;
    /** open=开放平台扫码 mp=小程序 */
    private String appType;
    private String nickname;
    private String avatarUrl;
}
