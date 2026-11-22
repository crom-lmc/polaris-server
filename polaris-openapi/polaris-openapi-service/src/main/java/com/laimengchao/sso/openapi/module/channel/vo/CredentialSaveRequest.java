package com.laimengchao.sso.openapi.module.channel.vo;

import lombok.Data;

/**
 * 维护渠道凭证请求：接收明文字段，服务端加密后立即丢弃明文，仅落 {@code ENC(...)} 密文。
 * <p>
 * 用 Map 承载以兼容不同渠道的凭证字段差异（邮件：user/password；后续短信/支付：accessKey/secret 等）。
 */
@Data
public class CredentialSaveRequest {

    /** 凭证明文 KV，如 {"user":"xxx@qq.com","password":"授权码"} */
    private java.util.Map<String, Object> credential;
}
