package com.laimengchao.sso.openapi.module.channel.vo;

import lombok.Data;

/** 新建/编辑出站渠道请求（不含凭证，凭证走独立的「维护凭证」接口）。 */
@Data
public class ChannelSaveRequest {

    private String channelCode;
    private String channelName;
    private String baseUrl;
    /** 认证类型：SMTP / API_KEY / OAUTH / SIGNATURE / NONE */
    private String authType;
    /** 非敏感配置 JSON 字符串 */
    private String authConfig;
    private Integer timeoutMs;
    private Integer retryMax;
    private String allowedApps;
    private Integer status;
    private String remark;
}
