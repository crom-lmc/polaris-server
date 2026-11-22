package com.laimengchao.sso.openapi.module.outbound.adapter;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** 出站调用结果：success + 渠道返回的 messageId/描述（已是脱敏后的安全内容）。 */
@Data
@Builder
@AllArgsConstructor
public class OutboundResult {

    private boolean success;
    private String messageId;
    private String detail;

    public static OutboundResult ok(String messageId, String detail) {
        return new OutboundResult(true, messageId, detail);
    }

    public static OutboundResult fail(String detail) {
        return new OutboundResult(false, null, detail);
    }
}
