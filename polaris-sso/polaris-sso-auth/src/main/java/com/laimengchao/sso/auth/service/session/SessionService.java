package com.laimengchao.sso.auth.service.session;

import com.laimengchao.sso.auth.config.AuthProperties;
import com.laimengchao.sso.auth.service.TokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

/**
 * 中心会话的读写与 cookie 落地。
 * <p>
 * 为什么用 cookie 而不是 localStorage 存会话：单点要求「跨应用共享登录态」，
 * localStorage 是按 origin 隔离的，blog 站点永远看不到 sso 站点写进去的东西；
 * 而 cookie 只要 Domain 设到顶级域，所有子域都能带上。
 * <p>
 * cookie 只存一个随机 sessionId，真正的 accountId 在服务端（Redis），
 * 所以前端拿不到也改不了「我是谁」。
 */
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SessionStore sessionStore;
    private final AuthProperties props;
    private final TokenService tokenService;

    /** 登录成功后建立中心会话，并把 sessionId 写进 HttpOnly cookie */
    public String create(Long accountId, HttpServletResponse response) {
        String sid = randomId();
        sessionStore.put(sid, accountId, props.getSessionTtlSeconds());
        writeCookie(response, sid, props.getSessionTtlSeconds());
        return sid;
    }

    /** 当前 cookie 对应的账户 ID；无会话返回 null */
    public Long currentAccountId(HttpServletRequest request) {
        String sid = readCookie(request);
        if (sid == null || sid.isBlank()) {
            return null;
        }
        return sessionStore.get(sid);
    }

    /** 当前中心会话 ID（即使服务端已失效也返回 cookie 里的值，供登出时精确作废） */
    public String currentSid(HttpServletRequest request) {
        String sid = readCookie(request);
        return sid == null || sid.isBlank() ? null : sid;
    }

    /** 会话有效期（秒），用于计算吊销条目的保留时长 */
    public long ttlSeconds() {
        return props.getSessionTtlSeconds();
    }

    /**
     * 单点登出：删服务端会话 + 让浏览器立刻丢掉 cookie。
     *
     * @return 被销毁的 sid（cookie 不存在时为 null）。调用方还要拿它去作废
     *         同样绑在这个会话上的其他应用令牌，否则只删了自己这一份。
     */
    public String destroy(HttpServletRequest request, HttpServletResponse response) {
        String sid = readCookie(request);
        if (sid != null && !sid.isBlank()) {
            sessionStore.remove(sid);
        }
        writeCookie(response, "", 0);
        return sid == null || sid.isBlank() ? null : sid;
    }

    /**
     * 管理端强制下线：作废该账号所有中心会话（含各应用绑定的令牌）。
     * 逐个 sid 调用 TokenService.logout，使网关的吊销名单同步生效。
     *
     * @return 被作废的会话数
     */
    public int forceLogout(Long accountId) {
        Set<String> sids = sessionStore.sidsOf(accountId);
        for (String sid : sids) {
            tokenService.logout(null, null, sid);
            sessionStore.remove(sid);
        }
        return sids.size();
    }

    // ---------------- cookie 细节 ----------------

    private void writeCookie(HttpServletResponse response, String value, long maxAgeSeconds) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie
                .from(props.getSessionCookieName(), value)
                .path("/")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .httpOnly(true)
                .secure(props.isSessionCookieSecure())
                .sameSite(props.getSessionCookieSameSite());

        // Domain 为空时必须不设置：写 domain("") 会生成非法 cookie，浏览器直接丢弃
        String domain = props.getSessionCookieDomain();
        if (domain != null && !domain.isBlank()) {
            b.domain(domain.trim());
        }
        response.addHeader(HttpHeaders.SET_COOKIE, b.build().toString());
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = props.getSessionCookieName();
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private String randomId() {
        byte[] buf = new byte[24];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
