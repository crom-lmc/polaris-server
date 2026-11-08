package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 微信绑定反查请求（对应 sso-account 的 /internal/accounts/wechat/by-openid）。
 */
@Data
public class WechatOpenidRequest {

    private String openid;
    private String appType;
}
