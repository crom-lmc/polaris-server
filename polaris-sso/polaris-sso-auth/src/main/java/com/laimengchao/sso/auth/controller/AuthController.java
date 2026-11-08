package com.laimengchao.sso.auth.controller;

import com.laimengchao.sso.auth.config.AuthProperties;
import com.laimengchao.sso.auth.dto.AppBrief;
import com.laimengchao.sso.auth.dto.AuthUser;
import com.laimengchao.sso.auth.dto.EmailCodeRequest;
import com.laimengchao.sso.auth.dto.EmailLoginRequest;
import com.laimengchao.sso.auth.dto.LoginRequest;
import com.laimengchao.sso.auth.dto.RefreshRequest;
import com.laimengchao.sso.auth.dto.TokenResponse;
import com.laimengchao.sso.auth.dto.WechatLoginRequest;
import com.laimengchao.sso.auth.service.AccountClient;
import com.laimengchao.sso.auth.service.EmailSender;
import com.laimengchao.sso.auth.service.TokenService;
import com.laimengchao.sso.auth.service.WechatService;
import com.laimengchao.sso.auth.service.session.SessionService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 认证入口。网关把 /api/auth/login、/api/auth/refresh、/api/auth/logout、/api/auth/email/**、
 * /api/auth/wechat/login 放进白名单，其余接口要求先带 token。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TokenService tokenService;
    private final AccountClient accountClient;
    private final AuthProperties props;
    private final EmailSender emailSender;
    private final WechatService wechatService;
    private final SessionService sessionService;

    /**
     * 账号密码登录。除了签发 JWT，还会建立一个中心会话并写 HttpOnly cookie——
     * 这是「一次登录、多应用免登」的凭据；JWT 只给当前应用用。
     */
    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                       HttpServletRequest request, HttpServletResponse response) {
        AuthUser user = tokenService.authenticate(req.getUsername(), req.getPassword(),
                clientIp(request), request.getHeader("User-Agent"), "password", req.getAppCode());
        // 先建会话再签发：令牌里要带 sid，否则这次登录不受「一处登出、处处失效」管辖
        String sid = sessionService.create(user.getAccountId(), response);
        return R.ok(tokenService.issuePair(user, sid));
    }

    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return R.ok(tokenService.refresh(req.getRefreshToken()));
    }

    /**
     * 登出：吊销 access/refresh + 销毁中心会话。
     * <p>顺序很重要——先删服务端会话再清 cookie，否则中途失败会留下「cookie 没了但会话还在」
     * 的悬空状态（虽然不影响安全，但会让单点在 7 天内莫名复活）。
     */
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestBody(required = false) RefreshRequest req,
                               HttpServletRequest request, HttpServletResponse response) {
        // 先摘 cookie 拿到 sid（顺序不能反：destroy 之后 cookie 就没了），
        // 再拿它去作废同一会话在其他应用里签出的全部令牌
        String sid = sessionService.destroy(request, response);
        tokenService.logout(bearer(authorization), req == null ? null : req.getRefreshToken(), sid);
        return R.ok();
    }

    /**
     * 当前登录者身份。
     *
     * <p>默认返回令牌里携带的快照（不查库，零开销）。带 {@code ?app=blog} 时改为
     * **实时查库**返回该应用的权限码——这是业务应用推荐的调用方式：改完授权立即生效，
     * 不必等令牌过期。
     */
    @GetMapping("/me")
    public R<AuthUser> me(@RequestHeader(value = "Authorization", required = false) String authorization,
                               @RequestParam(required = false) String app) {
        String token = bearer(authorization);
        if (token == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        try {
            Claims claims = tokenService.parseAccess(token);
            Long accountId = Long.valueOf(claims.getSubject());
            if (app != null && !app.isBlank()) {
                return R.ok(accountClient.principal(accountId, app));
            }
            AuthUser user = new AuthUser();
            user.setAccountId(accountId);
            user.setUsername(claims.get(JwtClaims.USERNAME, String.class));
            user.setNickname(claims.get(JwtClaims.NICKNAME, String.class));
            user.setSuperAdmin(Boolean.TRUE.equals(claims.get(JwtClaims.SUPER_ADMIN, Boolean.class)));
            user.setAppCode(claims.get(JwtClaims.APP, String.class));
            String perms = claims.get(JwtClaims.PERMISSIONS, String.class);
            user.setPermissions(splitCsv(perms));
            user.setApps(splitCsv(claims.get(JwtClaims.APPS, String.class)));
            return R.ok(user);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌无效或已过期");
        }
    }

    /** 启用中的应用清单：待办「应用」筛选用，不做账号权限过滤（登录即可见）。 */
    @GetMapping("/apps")
    public R<List<AppBrief>> apps() {
        return R.ok(accountClient.enabledApps());
    }

    /** 发送邮箱验证码：SMTP 已配则发信，否则打印到控制台（本地调试）。 */
    @PostMapping("/email/code")
    public R<Void> sendEmailCode(@Valid @RequestBody EmailCodeRequest req, HttpServletRequest request) {
        String code = accountClient.createEmailCode(req.getEmail(), "login", clientIp(request));
        emailSender.send(req.getEmail(), "SSO 登录验证码",
                "您的验证码为：" + code + "，有效期 " + props.getEmailCodeTtlMinutes() + " 分钟。");
        return R.ok();
    }

    /** 邮箱验证码登录：校验通过后签发令牌，同样建立中心会话以支持跨应用单点。 */
    @PostMapping("/email/login")
    public R<TokenResponse> emailLogin(@Valid @RequestBody EmailLoginRequest req,
                                            HttpServletResponse response) {
        Long accountId = accountClient.verifyEmail(req.getEmail(), "login", req.getCode());
        AuthUser user = accountClient.principal(accountId);
        String sid = sessionService.create(user.getAccountId(), response);
        return R.ok(tokenService.issuePair(user, sid));
    }

    /** 微信扫码登录：未配置开放平台时返回 501（启用 M4 请填写 appid/appsecret）。 */
    @PostMapping("/wechat/login")
    public R<TokenResponse> wechatLogin(@Valid @RequestBody WechatLoginRequest req,
                                             HttpServletResponse response) {
        if (!props.isWechatEnabled()) {
            return R.fail(501, "微信登录未启用（未配置开放平台 appid）");
        }
        String openid = wechatService.exchangeOpenid(req.getCode());
        Long accountId = accountClient.accountIdByOpenid(openid, "open");
        if (accountId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "该微信未绑定账户，请先使用密码登录后在账户页绑定");
        }
        AuthUser user = accountClient.principal(accountId);
        String sid = sessionService.create(user.getAccountId(), response);
        return R.ok(tokenService.issuePair(user, sid));
    }

    /** 服务间：供网关拉取已吊销的 jti（无共享存储时的轻量同步方案）。 */
    @GetMapping("/internal/revoked")
    public R<Map<String, Object>> revoked() {
        return R.ok(Map.of("items", tokenService.revokedSnapshot()));
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 逗号分隔字符串 -> 列表，空值返回空列表（perms / apps 共用） */
    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String bearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String v = authorization.trim();
        return v.regionMatches(true, 0, "Bearer ", 0, 7) ? v.substring(7).trim() : v;
    }
}
