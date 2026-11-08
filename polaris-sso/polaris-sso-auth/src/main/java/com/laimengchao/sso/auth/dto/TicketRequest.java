package com.laimengchao.sso.auth.dto;

import lombok.Data;

/** 申请一次性票据（已登录状态下换 ticket）。 */
@Data
public class TicketRequest {

    /** 目标应用编码 */
    private String appCode;

    /**
     * 换完票据后要跳回的地址（应用侧接收 ticket 的接口）。
     * 服务端会校验它是否属于该应用的 origin，不合法则回落到应用注册的 baseUrl。
     */
    private String redirect;
}
