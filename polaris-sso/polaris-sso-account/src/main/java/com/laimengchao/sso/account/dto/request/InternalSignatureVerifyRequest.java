package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 服务间签名验签请求：接口开放平台把第三方的签名材料转发过来，
 * 由账号中心用库里的 app_secret 重算 HMAC 比对——密钥全程不出账号域。
 */
@Data
public class InternalSignatureVerifyRequest {

    /** 第三方应用编码 */
    private String appCode;
    /** Unix 秒级时间戳 */
    private String timestamp;
    /** 随机串（防重放，由开放平台缓存去重） */
    private String nonce;
    /** 待签原文（开放平台按约定拼装的规范串） */
    private String content;
    /** 第三方签名（Hex 编码的 HMAC-SHA256） */
    private String signature;
}
