package com.laimengchao.sso.auth.service;

import com.laimengchao.sso.auth.config.AuthProperties;
import com.laimengchao.sso.auth.dto.AuthUser;
import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.util.KeySources;
import com.laimengchao.sso.util.PemUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * RS256 令牌签发与解析。
 * <p>
 * 只有本服务持有私钥；网关只拿公钥验签，无法伪造令牌——这正是非对称签名的意义。
 */
@Service
public class JwtService {

    private final AuthProperties props;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtService(AuthProperties props) {
        this.props = props;
        // 密钥来自配置中心（内联 PEM 或地址），异常信息里不要带出密钥内容
        try (InputStream in = KeySources.open(props.getPrivateKey())) {
            this.privateKey = PemUtils.parsePrivateKey(in);
        } catch (Exception e) {
            throw new IllegalStateException("加载 JWT 私钥失败：" + e.getMessage(), e);
        }
        try (InputStream in = KeySources.open(props.getPublicKey())) {
            this.publicKey = PemUtils.parsePublicKey(in);
        } catch (Exception e) {
            throw new IllegalStateException("加载 JWT 公钥失败：" + e.getMessage(), e);
        }
    }

    /** 签发 access token，携带账户身份与权限码。 */
    public Issued issueAccess(AuthUser user) {
        return issueAccess(user, null);
    }

    /** 签发 access token，并绑定中心会话（sid 用于跨应用集体登出）。 */
    public Issued issueAccess(AuthUser user, String sid) {
        return issue(user, JwtClaims.TYPE_ACCESS, props.getAccessTtlSeconds(), sid);
    }

    /** 签发 refresh token，不带权限码（只用于换新 access）。 */
    public Issued issueRefresh(AuthUser user) {
        return issueRefresh(user, null);
    }

    public Issued issueRefresh(AuthUser user, String sid) {
        return issue(user, JwtClaims.TYPE_REFRESH, props.getRefreshTtlSeconds(), sid);
    }

    private Issued issue(AuthUser user, String type, long ttlSeconds, String sid) {
        String jti = UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(ttlSeconds);

        List<String> perms = user.getPermissions() == null ? List.of() : user.getPermissions();
        List<String> apps = user.getApps() == null ? List.of() : user.getApps();
        String app = (user.getAppCode() == null || user.getAppCode().isBlank())
                ? JwtClaims.PLATFORM_APP : user.getAppCode();
        var builder = Jwts.builder()
                .issuer(props.getIssuer())
                .subject(String.valueOf(user.getAccountId()))
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claim(JwtClaims.USERNAME, user.getUsername())
                .claim(JwtClaims.NICKNAME, user.getNickname())
                .claim(JwtClaims.SUPER_ADMIN, user.isSuperAdmin())
                // 作用域两种令牌都带：刷新时要靠它重新拉对应应用的权限，否则会串域
                .claim(JwtClaims.APP, app)
                .claim(JwtClaims.TOKEN_TYPE, type);
        // 有中心会话就带上 sid：这是「一端登出、全部失效」的挂钩点
        if (sid != null && !sid.isBlank()) {
            builder.claim(JwtClaims.SESSION_ID, sid);
        }
        // refresh 不重复塞权限码/应用码，避免令牌过大且授权变更后残留旧快照
        if (JwtClaims.TYPE_ACCESS.equals(type)) {
            builder.claim(JwtClaims.PERMISSIONS, String.join(",", perms))
                    .claim(JwtClaims.APPS, String.join(",", apps));
        }
        String token = builder.signWith(privateKey, Jwts.SIG.RS256).compact();
        return new Issued(token, jti, ttlSeconds);
    }

    /** 公钥（用于 /api/auth/public/jwks 与公钥分发，业务应用可脱离网关自校验令牌） */
    public PublicKey publicKey() {
        return publicKey;
    }

    /** 验签并解析；非法/过期会抛 io.jsonwebtoken.JwtException。 */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(props.getIssuer())
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    /** 签发结果：令牌本体 + jti（用于吊销）+ 有效期秒数。 */
    public record Issued(String token, String jti, long ttlSeconds) {
    }
}
