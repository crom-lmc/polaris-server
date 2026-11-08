package com.laimengchao.sso.auth.controller;

import com.laimengchao.sso.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公钥分发。**无需鉴权**（公钥本就是公开信息）。
 *
 * <p>为什么需要它：网关能验签，但业务应用不一定跑在这个网关后面（自建 Nginx、
 * 独立容器、完全不同的技术栈）。有了 JWKS，它们可以自己验签，
 * 不必为每个请求回源 SSO 拿身份，也不必共享私钥。
 *
 * <p>{@code kid} 用公钥指纹而非固定值：将来轮换密钥时，验签方能按 kid 命中正确的那把，
 * 不会因为缓存了旧公钥而把所有令牌判成非法。
 */
@RestController
@RequestMapping("/api/auth/public")
@RequiredArgsConstructor
public class PublicKeyController {

    private final JwtService jwtService;

    /** 标准 JWKS 文档，可直接交给 jjwt / nimbus / Spring Security 的 JWKSource 使用 */
    @GetMapping(value = "/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        RSAPublicKey key = (RSAPublicKey) jwtService.publicKey();
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", keyId(key));
        jwk.put("n", b64Url(unsignedBytes(key.getModulus().toByteArray())));
        jwk.put("e", b64Url(unsignedBytes(key.getPublicExponent().toByteArray())));
        return Map.of("keys", List.of(jwk));
    }

    /** PEM 文本（给不解析 JWKS 的场景，如直接用 openssl / PyJWT 验签） */
    @GetMapping(value = "/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    public String publicKeyPem() {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(jwtService.publicKey().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----\n";
    }

    private String keyId(RSAPublicKey key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
            byte[] head = new byte[8];
            System.arraycopy(digest, 0, head, 0, 8);
            return b64Url(head);
        } catch (Exception e) {
            return "sso-default";
        }
    }

    /** BigInteger.toByteArray() 可能带前导 0 符号位，JWK 要求无符号大端 */
    private byte[] unsignedBytes(byte[] raw) {
        if (raw.length > 1 && raw[0] == 0) {
            byte[] out = new byte[raw.length - 1];
            System.arraycopy(raw, 1, out, 0, out.length);
            return out;
        }
        return raw;
    }

    private String b64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
