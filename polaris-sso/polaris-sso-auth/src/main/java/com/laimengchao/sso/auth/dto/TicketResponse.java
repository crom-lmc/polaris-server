package com.laimengchao.sso.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 票据下发结果。`redirectTo` 已经拼好 ticket 参数，前端直接 302 过去即可。 */
@Data
@AllArgsConstructor
public class TicketResponse {

    private String ticket;
    private String appCode;
    private String appName;
    /** 已带 ticket 参数的最终跳转地址 */
    private String redirectTo;
    /** 票据有效期（秒） */
    private long expiresIn;
}
