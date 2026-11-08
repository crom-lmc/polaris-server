package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 服务间启停账号请求（对应 sso-account 的 /internal/accounts/{id}/status）。
 */
@Data
public class AccountStatusRequest {

    private boolean enabled;
}
