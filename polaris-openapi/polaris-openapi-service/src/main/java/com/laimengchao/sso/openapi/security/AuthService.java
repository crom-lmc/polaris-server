package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.api.OpenapiAccountApi;
import com.laimengchao.sso.client.dto.AppVerifyRequest;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.client.dto.SignatureVerifyRequest;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 第三方鉴权编排：两种模式最终都落到 {@link CallerIdentity}（appCode + 权限码）。
 * <ul>
 *   <li>client_credentials：app_code/app_secret 经账号中心 verifyApp 校验 → 取该应用权限码 → 自签短期令牌；</li>
 *   <li>签名模式：时间戳偏差 + nonce 去重后，把签名材料转发账号中心用 app_secret 重算 HMAC（密钥不出账号域）→ 取权限码。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AccountApi accountApi;
    private final OpenapiAccountApi openapiAccountApi;
    private final AccessTokenService accessTokenService;
    private final NonceCache nonceCache;

    @Value("${openapi.signature-tolerance-seconds:300}")
    private long signatureToleranceSeconds;

    /** client_credentials 换令牌：凭据校验 + 权限查询 + 自签。 */
    public TokenResult issueToken(String appCode, String appSecret) {
        if (appCode == null || appCode.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new BizException(OpenapiErrorCode.SIGN_INVALID, "app_code 与 app_secret 必填");
        }
        R<?> verify;
        try {
            AppVerifyRequest req = new AppVerifyRequest();
            req.setCode(appCode);
            req.setSecret(appSecret);
            verify = accountApi.verifyApp(req);
        } catch (Exception e) {
            log.warn("应用凭据校验调用失败：appCode={} msg={}", appCode, e.getMessage());
            throw new BizException(OpenapiErrorCode.SIGN_INVALID);
        }
        if (verify == null || !verify.ok()) {
            throw new BizException(OpenapiErrorCode.SIGN_INVALID);
        }
        CallerIdentity identity = buildIdentity(appCode);
        String token = accessTokenService.issue(identity);
        return new TokenResult(token, accessTokenService.ttlSeconds());
    }

    /** 签名模式鉴权：校验时间戳/nonce，转发账号中心验签，通过后返回调用方身份。 */
    public CallerIdentity authenticateSignature(String appCode, String timestamp,
                                                String nonce, String content, String signature) {
        if (appCode == null || appCode.isBlank() || timestamp == null || nonce == null
                || content == null || signature == null) {
            throw new BizException(OpenapiErrorCode.SIGN_INVALID);
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new BizException(OpenapiErrorCode.SIGN_INVALID);
        }
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > signatureToleranceSeconds) {
            throw new BizException(OpenapiErrorCode.SIGN_EXPIRED);
        }
        if (!nonceCache.markOnce(appCode, nonce)) {
            throw new BizException(OpenapiErrorCode.REPLAY_DETECTED);
        }
        SignatureVerifyRequest req = new SignatureVerifyRequest();
        req.setAppCode(appCode);
        req.setTimestamp(timestamp);
        req.setNonce(nonce);
        req.setContent(content);
        req.setSignature(signature);
        R<Boolean> result;
        try {
            result = openapiAccountApi.verifySignature(req);
        } catch (Exception e) {
            log.warn("签名验签调用失败：appCode={} msg={}", appCode, e.getMessage());
            throw new BizException(OpenapiErrorCode.SIGN_INVALID);
        }
        if (result == null || !result.ok() || !Boolean.TRUE.equals(result.getData())) {
            throw new BizException(OpenapiErrorCode.SIGN_INVALID);
        }
        return buildIdentity(appCode);
    }

    /** 从统一登录中心取该应用注册的开放 API 权限码。 */
    private CallerIdentity buildIdentity(String appCode) {
        R<List<String>> resp;
        try {
            resp = openapiAccountApi.appPermissions(appCode);
        } catch (Exception e) {
            log.error("查询应用开放权限失败：appCode={}", appCode, e);
            throw new BizException(OpenapiErrorCode.CONFIG_ERROR);
        }
        if (resp == null || !resp.ok()) {
            throw new BizException(OpenapiErrorCode.PERM_DENIED);
        }
        Set<String> perms = new HashSet<>(resp.getData() == null ? List.of() : resp.getData());
        return new CallerIdentity(appCode, perms);
    }

    /** 换令牌结果。 */
    public record TokenResult(String accessToken, long expiresIn) {
    }
}
