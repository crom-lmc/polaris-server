package com.laimengchao.sso.auth.controller;

import com.laimengchao.sso.auth.dto.AppBrief;
import com.laimengchao.sso.auth.dto.AuthUser;
import com.laimengchao.sso.auth.dto.TokenResponse;
import com.laimengchao.sso.auth.service.AccountClient;
import com.laimengchao.sso.auth.service.TokenService;
import com.laimengchao.sso.auth.service.oauth.OAuthCodeStore;
import com.laimengchao.sso.auth.service.oauth.OAuthService;
import com.laimengchao.sso.auth.service.session.SessionService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 标准端点（A 档）。
 * <p>
 * {@code /authorize}：浏览器侧跳转入口。与 /ticket 一样靠 HttpOnly 中心会话 cookie
 * （或 Bearer）识别身份，所以在网关白名单里；签码后前端 {@code location.replace} 回应用。
 * 自有应用一律静默签码（consent 是给不可信第三方的，内部应用不需要）。
 * <p>
 * {@code /token}：应用后端服务器到服务器调用，靠 client_secret（或 PKCE）准入，
 * 同样在白名单里。<b>响应是裸 OAuth2 JSON</b>（access_token/token_type/expires_in/
 * refresh_token/scope），不带平台的 Result 壳——标准客户端库只认协议格式。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;
    private final SessionService sessionService;
    private final AccountClient accountClient;
    private final TokenService tokenService;

    // ---------------- authorize ----------------

    /**
     * 签发授权码。请求体（JSON）：response_type=code、client_id、redirect_uri、
     * scope?、state?、code_challenge?、code_challenge_method?。
     * response_type 必须是 code（A 档只实现授权码，不支持 implicit）。
     */
    @PostMapping("/authorize")
    public R<Map<String, Object>> authorize(@RequestBody Map<String, String> req,
                                                 HttpServletRequest request,
                                                 @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long accountId = sessionService.currentAccountId(request);
        String sid = sessionService.currentSid(request);
        if (accountId == null) {
            Claims claims = accessClaims(authorization);
            if (claims != null) {
                accountId = Long.valueOf(claims.getSubject());
                sid = claims.get(JwtClaims.SESSION_ID, String.class);
            }
        }
        if (accountId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法完成授权");
        }
        if (!"code".equals(req.get("responseType")) && !"code".equals(req.get("response_type"))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "response_type 仅支持 code");
        }
        List<AppBrief> apps = accountClient.apps(accountId);
        OAuthService.AuthorizeResult result = oauthService.issue(
                accountId,
                req.get("clientId") != null ? req.get("clientId") : req.get("client_id"),
                req.get("redirectUri") != null ? req.get("redirectUri") : req.get("redirect_uri"),
                req.get("scope"),
                req.get("state"),
                req.get("codeChallenge") != null ? req.get("codeChallenge") : req.get("code_challenge"),
                req.get("codeChallengeMethod") != null ? req.get("codeChallengeMethod") : req.get("code_challenge_method"),
                apps,
                sid);
        return R.ok(Map.of(
                "code", result.code(),
                "redirectTo", result.redirectTo(),
                "expiresIn", result.expiresIn()));
    }

    // ---------------- token ----------------

    /**
     * 换令牌。标准协议用 application/x-www-form-urlencoded（@RequestParam 同时兼容
     * query 参数），client 凭据支持 client_secret_post 与 HTTP Basic 两种。
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(required = false) String grantType,
            @RequestParam(required = false) String grant_type,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) String client_id,
            @RequestParam(required = false) String clientSecret,
            @RequestParam(required = false) String client_secret,
            @RequestParam(required = false) String redirectUri,
            @RequestParam(required = false) String redirect_uri,
            @RequestParam(required = false) String codeVerifier,
            @RequestParam(required = false) String code_verifier,
            @RequestParam(required = false) String refreshToken,
            @RequestParam(required = false) String refresh_token,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        String grant = firstNonBlank(grantType, grant_type);
        String cid = firstNonBlank(clientId, client_id);
        String csec = firstNonBlank(clientSecret, client_secret);
        String ruri = firstNonBlank(redirectUri, redirect_uri);

        // Basic 头里的凭据优先级低于表单字段（协议允许两者，这里显式合并）
        if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()),
                        StandardCharsets.UTF_8);
                int colon = decoded.indexOf(':');
                if (colon > 0) {
                    if (cid == null || cid.isBlank()) {
                        cid = URLDecoder.decode(decoded.substring(0, colon), StandardCharsets.UTF_8);
                    }
                    if (csec == null || csec.isBlank()) {
                        csec = URLDecoder.decode(decoded.substring(colon + 1), StandardCharsets.UTF_8);
                    }
                }
            } catch (IllegalArgumentException ignored) {
                return error(401, "invalid_client", "Basic 凭据无法解析");
            }
        }

        if (cid == null || cid.isBlank()) {
            return error(400, "invalid_request", "client_id 必填");
        }
        // 客户端准入：secret 错 / 应用停用一律 invalid_client，不区分细节
        if (!accountClient.verifyApp(cid, csec == null ? "" : csec)) {
            return error(401, "invalid_client", "客户端凭据无效");
        }

        if ("authorization_code".equals(grant)) {
            return tokenByCode(code, cid, ruri,
                    firstNonBlank(codeVerifier, code_verifier));
        }
        if ("refresh_token".equals(grant)) {
            return tokenByRefresh(firstNonBlank(refreshToken, refresh_token), cid);
        }
        return error(400, "unsupported_grant_type", "仅支持 authorization_code / refresh_token");
    }

    private ResponseEntity<Map<String, Object>> tokenByCode(String code, String clientId,
                                                            String redirectUri, String verifier) {
        if (code == null || code.isBlank()) {
            return error(400, "invalid_request", "code 必填");
        }
        OAuthCodeStore.CodePayload payload = oauthService.take(code);
        if (payload == null) {
            return error(400, "invalid_grant", "授权码无效、已使用或已过期");
        }
        if (!clientId.equals(payload.clientId())) {
            log.warn("授权码归属应用不一致：请求={} 码={}", clientId, payload.clientId());
            return error(400, "invalid_grant", "授权码与客户端不匹配");
        }
        if (payload.redirectUri() != null && !payload.redirectUri().isBlank()
                && !payload.redirectUri().equals(redirectUri == null ? "" : redirectUri.trim())) {
            return error(400, "invalid_grant", "redirect_uri 与授权请求时不一致");
        }
        if (!oauthService.verifyPkce(payload, verifier)) {
            return error(400, "invalid_grant", "PKCE 校验失败");
        }
        // 授权可能已被回收：签发前再确认一次
        List<AppBrief> apps = accountClient.apps(payload.accountId());
        boolean allowed = apps != null && apps.stream().anyMatch(a -> clientId.equals(a.getCode()));
        if (!allowed) {
            return error(403, "access_denied", "该账号已无权访问此应用");
        }
        return ok(issueFor(payload.accountId(), clientId, payload.scope(), payload.sid()));
    }

    private ResponseEntity<Map<String, Object>> tokenByRefresh(String refreshToken, String clientId) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return error(400, "invalid_request", "refresh_token 必填");
        }
        try {
            TokenResponse pair = tokenService.refreshForApp(refreshToken, clientId);
            return ok(pair);
        } catch (BizException e) {
            // 403（应用不匹配）保持原语义，其余按协议回 invalid_grant
            if (e.getCode() == ErrorCode.FORBIDDEN.getCode()) {
                return error(403, "access_denied", e.getMessage());
            }
            return error(400, "invalid_grant", e.getMessage());
        }
    }

    private TokenResponse issueFor(Long accountId, String clientId, String scope, String sid) {
        AuthUser principal = accountClient.principal(accountId, clientId);
        // 继承授权时的中心会话：SSO 侧登出后，这里换出去的应用令牌一并失效
        return tokenService.issuePair(principal, sid);
    }

    /** 标准 OAuth2 成功响应：裸 JSON，无 Result 壳 */
    private ResponseEntity<Map<String, Object>> ok(TokenResponse pair) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", pair.getAccessToken());
        body.put("token_type", "Bearer");
        body.put("expires_in", pair.getExpiresIn());
        if (pair.getRefreshToken() != null) {
            body.put("refresh_token", pair.getRefreshToken());
        }
        body.put("scope", "api");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store")
                .header("Pragma", "no-cache")
                .body(body);
    }

    /** 标准 OAuth2 错误响应：{error, error_description} */
    private ResponseEntity<Map<String, Object>> error(int status, String code, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", description);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store")
                .body(body);
    }

    // ---------------- 内部工具 ----------------

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private Claims accessClaims(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String v = authorization.trim();
        String token = v.regionMatches(true, 0, "Bearer ", 0, 7) ? v.substring(7).trim() : v;
        try {
            return tokenService.parseAccess(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
