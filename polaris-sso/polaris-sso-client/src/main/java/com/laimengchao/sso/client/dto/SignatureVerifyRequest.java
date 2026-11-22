package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/** 转发给账号中心的签名验签材料（密钥不出账号域）。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignatureVerifyRequest {

    private String appCode;
    private String timestamp;
    private String nonce;
    private String content;
    private String signature;
}
