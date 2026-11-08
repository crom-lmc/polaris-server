package com.laimengchao.sso.client.dto;

import lombok.Data;

/**
 * 应用凭据校验请求（对应 sso-account 的 /internal/apps/verify）。
 */
@Data
public class AppVerifyRequest {

    private String code;
    private String secret;
}
