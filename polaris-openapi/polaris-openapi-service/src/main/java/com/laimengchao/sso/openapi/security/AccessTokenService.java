package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 开放平台自签短期访问令牌（HS256）。
 * <p>
 * 为什么不复用 sso-auth 的 RS256 令牌：第三方是「应用」而非「账号」，不应拿到平台用户令牌；
 * 且开放平台只需自证「这个 app_code 被授予了这些 API 权限」，短期自签令牌足够，
 * 私钥/密钥不出本服务，验签也只在本服务做——不污染其他服务。
 */
@Slf4j
@Service
public class AccessTokenService {

    private static final String ISSUER = "polaris-openapi";
    private static final String CLAIM_PERMS = JwtClaims.PERMISSIONS;

    private final SecretKey key;
    private final long ttlSeconds;

    public AccessTokenService(@Value("${openapi.token-secret}") String secret,
                              @Value("${openapi.token-ttl-seconds:3600}") long ttlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("openapi.token-secret 长度不足 32 字节，拒绝启动");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlSeconds = ttlSeconds;
    }

    /** 签发短期令牌。 */
    public String issue(CallerIdentity identity) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(identity.getAppCode())
                .id(UUID.randomUUID().toString().replace("-", ""))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claim(JwtClaims.APP, identity.getAppCode())
                .claim(CLAIM_PERMS, String.join(",", identity.getPermissions()))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 验签并还原调用方身份。签名错误/过期一律 401。 */
    public CallerIdentity verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String appCode = claims.get(JwtClaims.APP, String.class);
            String perms = claims.get(CLAIM_PERMS, String.class);
            if (appCode == null || appCode.isBlank()) {
                throw new BizException(OpenapiErrorCode.TOKEN_INVALID);
            }
            Set<String> set = new HashSet<>();
            if (perms != null && !perms.isBlank()) {
                for (String p : perms.split(",")) {
                    if (!p.isBlank()) {
                        set.add(p.trim());
                    }
                }
            }
            return new CallerIdentity(appCode, set);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.debug("开放平台令牌校验失败：{}", e.getMessage());
            throw new BizException(OpenapiErrorCode.TOKEN_INVALID);
        }
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }
}
