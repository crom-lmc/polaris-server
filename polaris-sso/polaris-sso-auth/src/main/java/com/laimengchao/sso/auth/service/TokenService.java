package com.laimengchao.sso.auth.service;

import com.laimengchao.sso.auth.config.AuthProperties;
import com.laimengchao.sso.auth.dto.AuthUser;
import com.laimengchao.sso.auth.dto.TokenResponse;
import com.laimengchao.sso.auth.service.revocation.RevocationStore;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * 令牌生命周期：登录签发、刷新轮换、登出吊销。
 * <p>
 * 吊销名单通过 {@link RevocationStore} 抽象：默认内存版（单实例够用），
 * 配置 Redis 后切换为共享版，多实例 + auth 重启都不丢。网关仍按原协议轮询 /internal/revoked。
 */
@Slf4j
@Service
public class TokenService {

    private final JwtService jwtService;
    private final AccountClient accountClient;
    private final AuthProperties props;
    private final RevocationStore revocationStore;

    public TokenService(JwtService jwtService, AccountClient accountClient, AuthProperties props,
                        RevocationStore revocationStore) {
        this.jwtService = jwtService;
        this.accountClient = accountClient;
        this.props = props;
        this.revocationStore = revocationStore;
    }

    /**
     * 凭据校验（不签发）。之所以把「认证」和「签发」拆开：登录时要先建立中心会话拿到 sid，
     * 再把 sid 写进令牌，否则这一对令牌就不属于任何会话，登出时没法连带作废。
     */
    public AuthUser authenticate(String username, String password, String clientIp,
                                 String userAgent, String loginType, String appCode) {
        return accountClient.authenticate(username, password, clientIp, userAgent, loginType, appCode);
    }

    public AuthUser authenticate(String username, String password, String clientIp,
                                 String userAgent, String loginType) {
        return authenticate(username, password, clientIp, userAgent, loginType, JwtClaims.PLATFORM_APP);
    }

    /**
     * 刷新：验签 → 校验是 refresh 类型 → 校验未被吊销 → 从 account 取**最新**权限
     * → 旧 refresh 立即作废（轮换，防止被窃取后长期复用）。
     *
     * <p>刷新沿用原令牌的 {@code app} 声明：如果换成平台域，博客刷新一次就拿到了
     * 管理台权限，等于越权。
     */
    public TokenResponse refresh(String refreshToken) {
        return refreshForApp(refreshToken, null);
    }

    /**
     * 刷新并校验令牌归属应用。
     *
     * @param expectedApp 期望的 app 声明；null 跳过校验（管理台自己的 /refresh 走这条路）。
     *                    OAuth2 标准端点用它把 refresh token 绑定到 client，防止
     *                    A 应用拿到的 refresh token 拿去 B 应用刷新（跨应用越权）
     */
    public TokenResponse refreshForApp(String refreshToken, String expectedApp) {
        Claims claims = parseOrThrow(refreshToken);
        if (!JwtClaims.TYPE_REFRESH.equals(claims.get(JwtClaims.TOKEN_TYPE, String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "该令牌不是 refresh token");
        }
        if (isRevoked(claims.getId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "refresh token 已失效，请重新登录");
        }
        // 会话维度也要拦：登出只作废了 sid，若这里不查，拿旧 refresh 一刷就复活了
        String sid = claims.get(JwtClaims.SESSION_ID, String.class);
        if (isSessionRevoked(sid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (expectedApp != null && !expectedApp.equals(claims.get(JwtClaims.APP, String.class))) {
            throw new BizException(ErrorCode.FORBIDDEN, "refresh token 与该应用不匹配");
        }
        Long accountId = Long.valueOf(claims.getSubject());
        String app = claims.get(JwtClaims.APP, String.class);
        // 重新拉取权限，保证刷新后拿到的是最新授权，而不是签发时的旧快照
        AuthUser user = accountClient.principal(accountId, app);
        revoke(claims.getId(), claims.getExpiration().toInstant());
        // 刷新换出来的一对令牌继续绑在同一个中心会话上，否则登出联动会断在这里
        return issuePair(user, sid);
    }

    /**
     * 登出：吊销 access / refresh 的 jti，并作废整个中心会话。
     * <p>
     * 关键在最后一步——只吊销 jti 的话，同一个浏览器里从别的应用换出来的令牌
     * （jti 不同、但同属一个 sid）依然有效，用户会觉得「退出了但系统还登着」。
     * sid 来源优先级：cookie 传进来的 > refresh 里的 claim > access 里的 claim。
     */
    public void logout(String accessToken, String refreshToken) {
        logout(accessToken, refreshToken, null);
    }

    public void logout(String accessToken, String refreshToken, String sid) {
        String session = sid;
        if (session == null || session.isBlank()) {
            session = sidOf(refreshToken);
        }
        if (session == null || session.isBlank()) {
            session = sidOf(accessToken);
        }
        revokeQuietly(accessToken);
        revokeQuietly(refreshToken);
        if (session != null && !session.isBlank()) {
            revokeSession(session);
        }
    }

    /** 验签 access token，并确认类型正确、未被登出吊销（jti + 会话双重校验）。 */
    public Claims parseAccess(String token) {
        Claims claims = parseOrThrow(token);
        if (!JwtClaims.TYPE_ACCESS.equals(claims.get(JwtClaims.TOKEN_TYPE, String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "该令牌不是 access token");
        }
        if (isRevoked(claims.getId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (isSessionRevoked(claims.get(JwtClaims.SESSION_ID, String.class))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return claims;
    }

    private void revokeQuietly(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtService.parse(token);
            revoke(claims.getId(), claims.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            // 令牌本来就无效，登出照样算成功
            log.debug("登出时忽略无效令牌: {}", e.getMessage());
        }
    }

    public TokenResponse issuePair(AuthUser user) {
        return issuePair(user, null);
    }

    /** 签发一对令牌；sid 非空即绑定中心会话，用于跨应用集体登出。 */
    public TokenResponse issuePair(AuthUser user, String sid) {
        JwtService.Issued access = jwtService.issueAccess(user, sid);
        JwtService.Issued refresh = jwtService.issueRefresh(user, sid);
        return new TokenResponse(access.token(), refresh.token(), "Bearer",
                access.ttlSeconds(), user);
    }

    private Claims parseOrThrow(String token) {
        try {
            return jwtService.parse(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌无效或已过期");
        }
    }

    private void revoke(String jti, Instant exp) {
        revocationStore.revoke(jti, exp);
    }

    private boolean isRevoked(String jti) {
        return revocationStore.isRevoked(jti);
    }

    /**
     * 作废整个中心会话。
     * <p>保留时长取 refresh 的有效期——超过这个时间，任何凭该会话签出的令牌都已自然过期，
     * 条目可以安全清理，不必无限堆积。
     */
    private void revokeSession(String sid) {
        if (sid == null || sid.isBlank()) {
            return;
        }
        revocationStore.revoke(JwtClaims.SID_REVOKE_PREFIX + sid,
                Instant.now().plusSeconds(props.getRefreshTtlSeconds()));
        log.info("中心会话已作废：{}", sid);
    }

    private boolean isSessionRevoked(String sid) {
        return sid != null && !sid.isBlank()
                && revocationStore.isRevoked(JwtClaims.SID_REVOKE_PREFIX + sid);
    }

    /** 从令牌里取 sid；令牌无效/无该 claim 返回 null（登出场景一律不算错）。 */
    private String sidOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            return jwtService.parse(token).get(JwtClaims.SESSION_ID, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 供网关同步吊销状态用的快照。 */
    public Map<String, Instant> revokedSnapshot() {
        return revocationStore.snapshot();
    }

    public long accessTtlSeconds() {
        return props.getAccessTtlSeconds();
    }
}
