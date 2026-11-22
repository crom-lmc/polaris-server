package com.laimengchao.sso.openapi.module.outbound.adapter;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 出站调用的统一入参：业务报文 + 已解密的渠道凭证 + 非敏感配置 + 超时。
 * <p>
 * 凭证只在这条链路里以明文存在，绝不写入日志或响应。
 */
@Data
@Builder
public class OutboundRequest {

    /** 渠道编码，如 email */
    private String channel;
    /** 业务报文（邮件：to/subject/body/cc） */
    private Map<String, Object> payload;
    /** 解密后的凭证（邮件：user/password） */
    private Map<String, Object> credential;
    /** 非敏感配置（邮件：host/port/from/ssl） */
    private Map<String, Object> config;
    /** 超时毫秒 */
    private int timeoutMs;
    /** 全链路追踪 ID */
    private String traceId;
}
