package com.laimengchao.sso.auth.service.oauth;

import com.laimengchao.sso.auth.config.AuthProperties;
import com.laimengchao.sso.auth.dto.AppBrief;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * OAuth2 标准授权码的签发与消费（A 档：协议形状标准，实现走自有 Redis/JWT）。
 * <p>
 * 完整链路：
 * <pre>
 * 应用 → 浏览器跳 SSO /authorize?response_type=code&amp;client_id=..&amp;redirect_uri=..&amp;state=..
 * SSO 有中心会话 → 静默签发授权码 → 302 回 redirect_uri?code=..&amp;state=..
 * 应用后端 → POST /oauth2/token（code + client_secret [+ code_verifier]）→ 拿 JWT 对
 * </pre>
 * 与 ticket 流程的关系：ticket 是「自家应用走的前门」，授权码是给标准 OAuth2 客户端
 * 库/第三方留的「标准门」——两者共用同一套账户、权限与 JWT 签发设施。
 */
@Slf4j
@Service
public class OAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthCodeStore codeStore;
    private final AuthProperties props;

    public OAuthService(OAuthCodeStore codeStore, AuthProperties props) {
        this.codeStore = codeStore;
        this.props = props;
    }

    /** 不带中心会话的签发（服务间/无 cookie 场景），换出的令牌不参与集体登出 */
    public AuthorizeResult issue(Long accountId, String clientId, String redirectUri,
                                 String scope, String state,
                                 String codeChallenge, String codeChallengeMethod,
                                 List<AppBrief> myApps) {
        return issue(accountId, clientId, redirectUri, scope, state,
                codeChallenge, codeChallengeMethod, myApps, null);
    }

    /**
     * 签发授权码（authorize 端点，用户身份由中心会话/Bearer 提供）。
     *
     * @param myApps 当前账号可访问的应用——clientId 必须在其中（应用存在 + 用户有授权，一次校验完成）
     * @param sid    授权时所在的中心会话，写进码里由 token 端点继承，用于跨应用集体登出
     */
    public AuthorizeResult issue(Long accountId, String clientId, String redirectUri,
                                 String scope, String state,
                                 String codeChallenge, String codeChallengeMethod,
                                 List<AppBrief> myApps, String sid) {
        if (clientId == null || clientId.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "client_id 必填");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "redirect_uri 必填");
        }
        AppBrief app = myApps == null ? null : myApps.stream()
                .filter(a -> clientId.equals(a.getCode()))
                .findFirst().orElse(null);
        if (app == null) {
            // 应用不存在与无权访问同话术返回，不帮外部探测应用列表
            throw new BizException(ErrorCode.FORBIDDEN, "无效的 client_id 或当前账号无权访问");
        }
        if (!isAllowedRedirect(redirectUri.trim(), app.getBaseUrl())) {
            log.warn("拒绝不合法的 OAuth2 回跳地址：{}（期望 origin 属于 {}）", redirectUri, app.getBaseUrl());
            throw new BizException(ErrorCode.PARAM_ERROR, "redirect_uri 与应用注册的访问地址不同源");
        }
        if (codeChallengeMethod != null && !codeChallengeMethod.isBlank()
                && !"S256".equalsIgnoreCase(codeChallengeMethod)
                && !"plain".equalsIgnoreCase(codeChallengeMethod)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的 code_challenge_method（仅 S256/plain）");
        }

        String code = randomCode();
        codeStore.save(code, new OAuthCodeStore.CodePayload(
                        accountId, clientId.trim(),
                        scope == null ? "" : scope.trim(),
                        redirectUri.trim(),
                        codeChallenge == null ? "" : codeChallenge.trim(),
                        codeChallengeMethod == null ? "" : codeChallengeMethod.trim(),
                        sid),
                props.getOauthCodeTtlSeconds());

        String target = buildRedirect(redirectUri.trim(), code, state);
        return new AuthorizeResult(code, target, props.getOauthCodeTtlSeconds());
    }

    /** 消费授权码（token 端点）：取走即删，一次性。 */
    public OAuthCodeStore.CodePayload take(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return codeStore.take(code);
    }

    /**
     * PKCE 校验：S256 = BASE64URL(SHA256(verifier))；plain = 原值比对。
     * 有 challenge 的码必须带 verifier，缺了直接拒——这是公共客户端的防截获底线。
     */
    public boolean verifyPkce(OAuthCodeStore.CodePayload payload, String verifier) {
        String challenge = payload.codeChallenge();
        if (challenge == null || challenge.isBlank()) {
            return true; // 机密客户端（带 appSecret）可以不走 PKCE
        }
        if (verifier == null || verifier.isBlank()) {
            return false;
        }
        String method = payload.codeChallengeMethod();
        if (method == null || method.isBlank() || "plain".equalsIgnoreCase(method)) {
            return constantTimeEquals(challenge, verifier);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            return constantTimeEquals(challenge, computed);
        } catch (Exception e) {
            return false;
        }
    }

    /** 换码端点用的最终跳转地址；state 原样带回（CSRF 防护靠调用方校验它） */
    public String buildRedirect(String redirectUri, String code, String state) {
        StringBuilder sb = new StringBuilder(redirectUri);
        sb.append(redirectUri.contains("?") ? '&' : '?');
        sb.append("code=").append(URLEncoder.encode(code, StandardCharsets.UTF_8));
        if (state != null && !state.isBlank()) {
            sb.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public record AuthorizeResult(String code, String redirectTo, long expiresIn) {
    }

    // ---------------- 内部工具 ----------------

    /**
     * 同 origin（协议 + 主机 + 端口）才放行，相对路径允许；与 ticket 同一套策略。
     */
    private boolean isAllowedRedirect(String redirect, String baseUrl) {
        if (!props.isOauthStrictRedirect()) {
            return true;
        }
        boolean relative = redirect.startsWith("/") && !redirect.startsWith("//");
        if (baseUrl == null || baseUrl.isBlank()) {
            return relative;
        }
        if (relative) {
            return true;
        }
        try {
            URI want = URI.create(redirect);
            URI allowed = URI.create(baseUrl);
            return eq(want.getScheme(), allowed.getScheme())
                    && eq(want.getHost(), allowed.getHost())
                    && effectivePort(want) == effectivePort(allowed);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean eq(String a, String b) {
        return a != null && a.equalsIgnoreCase(b == null ? "" : b);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    /** 字符串比较防时序侧信道（challenge/verifier 属于凭据材料） */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String randomCode() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
