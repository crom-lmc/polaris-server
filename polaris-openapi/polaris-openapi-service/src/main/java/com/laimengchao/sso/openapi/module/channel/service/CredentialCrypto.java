package com.laimengchao.sso.openapi.module.channel.service;

import com.laimengchao.sso.common.crypto.SecretCipher;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 渠道凭证加解密：复用 common 的 AES-GCM {@link SecretCipher}，主密钥来自配置
 * {@code openapi.channel.credential-key}（生产由环境变量/Nacos 下发）。
 * <p>
 * 库里只存 {@code ENC(...)} 密文，明文既不落库也不回显；主密钥与密文不匹配会直接抛异常，
 * 绝不静默降级，避免"凭证全丢但接口还返回成功"这种最难排查的故障。
 */
@Slf4j
@Component
public class CredentialCrypto {

    @Value("${openapi.channel.credential-key:}")
    private String keyRaw;

    private byte[] key;

    @PostConstruct
    public void init() {
        this.key = SecretCipher.parseKey(keyRaw);
        log.info("出站渠道凭证加密组件已初始化（AES-GCM，主密钥长度 {} 字节）", key.length);
    }

    /** 明文 → ENC(...) 密文。 */
    public String encrypt(String plain) {
        return SecretCipher.encrypt(plain, key);
    }

    /** ENC(...) 密文 → 明文；非密文原样返回（兼容历史空值/明文配置）。 */
    public String decrypt(String cipher) {
        return SecretCipher.decrypt(cipher, key);
    }

    /** 是否为密文。 */
    public boolean isEncrypted(String value) {
        return SecretCipher.isEncrypted(value);
    }
}
