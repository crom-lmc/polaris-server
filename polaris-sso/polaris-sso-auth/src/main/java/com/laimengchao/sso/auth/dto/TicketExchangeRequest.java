package com.laimengchao.sso.auth.dto;

import lombok.Data;

/**
 * 应用后端拿 ticket 换 token。
 * <p>
 * {@code appSecret} 是必须的：票据会出现在浏览器地址栏与日志里，
 * 若不校验调用方身份，任何人捡到 ticket 都能换成有效 JWT。
 */
@Data
public class TicketExchangeRequest {

    private String ticket;
    private String appCode;
    private String appSecret;
}
