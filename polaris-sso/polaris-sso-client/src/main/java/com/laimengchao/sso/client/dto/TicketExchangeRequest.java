package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 一次性票据换令牌请求（对应 sso-auth 的 /api/auth/ticket/exchange）。
 */
@Data
public class TicketExchangeRequest {

    /** 一次性、短时效、单次消费的 SSO 票据 */
    private String ticket;
    /** 发起换票的应用编码 */
    private String appCode;
    /** 应用密钥，用于证明调用方身份 */
    private String appSecret;
}
