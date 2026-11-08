package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/** 服务间校验应用密钥（auth 的 ticket exchange 调用）。 */
@Data
public class InternalAppVerifyRequest {

    private String code;
    private String secret;
}
