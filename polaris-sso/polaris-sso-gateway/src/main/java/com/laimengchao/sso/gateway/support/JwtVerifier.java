package com.laimengchao.sso.gateway.support;

import com.laimengchao.sso.gateway.config.GatewayProperties;
import com.laimengchao.sso.util.KeySources;
import com.laimengchao.sso.util.PemUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.PublicKey;

/**
 * 公钥验签。网关没有私钥，因此**无法**自行签发令牌——被攻破也伪造不出身份。
 */
@Component
public class JwtVerifier {

    private final PublicKey publicKey;
    private final String issuer;

    public JwtVerifier(GatewayProperties props) {
        this.issuer = props.getIssuer();
        try (InputStream in = KeySources.open(props.getPublicKey())) {
            this.publicKey = PemUtils.parsePublicKey(in);
        } catch (Exception e) {
            throw new IllegalStateException("网关加载 JWT 公钥失败：" + e.getMessage(), e);
        }
    }

    /** 验签并返回载荷；签名错误、过期、issuer 不符都会抛 {@link io.jsonwebtoken.JwtException}。 */
    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
