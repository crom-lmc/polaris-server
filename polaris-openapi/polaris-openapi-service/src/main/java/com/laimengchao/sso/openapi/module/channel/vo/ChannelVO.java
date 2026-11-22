package com.laimengchao.sso.openapi.module.channel.vo;

import lombok.Data;

/**
 * 出站渠道视图：凭证字段只回显「是否已配置」布尔值，密文/明文一律不出库到接口。
 */
@Data
public class ChannelVO {

    private String channelCode;
    private String channelName;
    private String baseUrl;
    private String authType;
    /** 非敏感认证配置 JSON（host/port/from/ssl） */
    private String authConfig;
    private Integer timeoutMs;
    private Integer retryMax;
    private String allowedApps;
    private Integer status;
    private String remark;
    /** 是否已配置凭证（true=credential_ref 非空），不回显凭证内容 */
    private boolean credentialConfigured;
}
