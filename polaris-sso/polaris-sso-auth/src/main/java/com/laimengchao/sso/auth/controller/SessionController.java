package com.laimengchao.sso.auth.controller;

import com.laimengchao.sso.auth.dto.AppBrief;
import com.laimengchao.sso.auth.dto.AuthUser;
import com.laimengchao.sso.auth.dto.SessionInfo;
import com.laimengchao.sso.auth.dto.TicketExchangeRequest;
import com.laimengchao.sso.auth.dto.TicketRequest;
import com.laimengchao.sso.auth.dto.TicketResponse;
import com.laimengchao.sso.auth.dto.TokenResponse;
import com.laimengchao.sso.auth.service.AccountClient;
import com.laimengchao.sso.auth.service.TokenService;
import com.laimengchao.sso.auth.service.session.SessionService;
import com.laimengchao.sso.auth.service.ticket.TicketService;
import com.laimengchao.sso.auth.service.ticket.TicketStore;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中心会话与跨应用单点。
 * <p>
 * 这些端点<b>不走 Bearer</b>：{@code /session}、{@code /ticket} 靠 HttpOnly cookie 识别身份
 * （正是为了在多个应用间共享登录态），{@code /ticket/exchange} 靠 appSecret 识别调用方，
 * 因此三者都在网关白名单里。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final AccountClient accountClient;
    private final TicketService ticketService;
    private final TokenService tokenService;

    /** 当前中心会话：已登录返回身份 + 可访问应用；未登录 401（前端据此决定是否显示登录框） */
    @GetMapping("/session")
    public R<SessionInfo> session(HttpServletRequest request) {
        Long accountId = sessionService.currentAccountId(request);
        if (accountId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未建立中心会话");
        }
        AuthUser principal = accountClient.principal(accountId);
        List<AppBrief> apps = accountClient.apps(accountId);
        return R.ok(toSessionInfo(principal, apps));
    }

    /**
     * 凭中心会话换一对令牌（静默登录）。
     * <p>
     * 用途：用户从应用 A 单点跳到 SSO 管理台时，管理台本地没有 token，
     * 但浏览器带着 SSO_SID cookie——这里直接换令牌，避免再让他输一次密码。
     * 对外部应用不开放这条路径（它们该走 ticket，且换出的 token 需要有应用边界）。
     */
    @PostMapping("/session/token")
    public R<TokenResponse> sessionToken(HttpServletRequest request) {
        String sid = sessionService.currentSid(request);
        Long accountId = sessionService.currentAccountId(request);
        if (accountId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未建立中心会话");
        }
        AuthUser principal = accountClient.principal(accountId);
        return R.ok(tokenService.issuePair(principal, sid));
    }

    /** 换一次性票据：已登录（cookie 或 Bearer 均可）且账号有该应用权限才发 */    @PostMapping("/ticket")
    public R<TicketResponse> ticket(@RequestBody TicketRequest req,
                                         HttpServletRequest request,
                                         @RequestHeader(value = "Authorization", required = false) String authorization) {
        Long accountId = sessionService.currentAccountId(request);
        String sid = sessionService.currentSid(request);
        if (accountId == null) {
            // 没有 cookie 时退回 Bearer：此时会话标识只能从令牌的 sid claim 里取，
            // 取不到就让票据裸奔（换出的令牌不参与集体登出），但不能因此拒绝换票
            Claims claims = accessClaims(authorization);
            if (claims == null) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "未登录，无法申请应用票据");
            }
            accountId = Long.valueOf(claims.getSubject());
            sid = claims.get(JwtClaims.SESSION_ID, String.class);
        }
        List<AppBrief> apps = accountClient.apps(accountId);
        return R.ok(ticketService.issue(accountId, req.getAppCode(), req.getRedirect(), apps, sid));
    }

    /**
     * 应用后端用 ticket 换 token。
     * <p>
     * 三重校验：① 调用方必须持有该应用的 appSecret；② 票据一次性且未过期；
     * ③ 票据归属的应用与请求的一致，且该账号此刻仍有此应用授权（授权被回收后不能再换）。
     */
    @PostMapping("/ticket/exchange")
    public R<TokenResponse> exchange(@RequestBody TicketExchangeRequest req) {
        if (req.getTicket() == null || req.getTicket().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "ticket 必填");
        }
        if (req.getAppCode() == null || req.getAppCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        if (!accountClient.verifyApp(req.getAppCode(), req.getAppSecret())) {
            // 不区分「应用不存在」「密钥错」「已停用」，避免被用于探测
            throw new BizException(ErrorCode.UNAUTHORIZED, "应用凭据无效");
        }

        TicketStore.TicketPayload payload = ticketService.take(req.getTicket());
        if (payload == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "票据无效、已使用或已过期");
        }
        if (!req.getAppCode().equals(payload.appCode())) {
            log.warn("票据归属应用不一致：请求={} 票据={}", req.getAppCode(), payload.appCode());
            throw new BizException(ErrorCode.FORBIDDEN, "票据与目标应用不匹配");
        }

        // 授权可能已被回收：签发前再确认一次，避免旧票据换出新 token
        List<AppBrief> apps = accountClient.apps(payload.accountId());
        boolean allowed = apps != null && apps.stream()
                .anyMatch(a -> req.getAppCode().equals(a.getCode()));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "该账号已无权访问此应用");
        }

        AuthUser principal = accountClient.principal(payload.accountId(), req.getAppCode());
        // 票据里带着签发时所在的中心会话，换出的令牌继承它 → 任一应用登出即可全线失效
        return R.ok(tokenService.issuePair(principal, payload.sid()));
    }

    /** 强制下线所需权限码（与 sso_permission 种子保持一致） */
    private static final String PERM_SESSION_MANAGE = "system:session:manage";

    /**
     * 管理端强制下线：作废目标账号所有中心会话。
     * <p>
     * auth 模块没有 @Permit 体系，这里手工解析 Bearer 并校验操作者权限：
     * 超管或持有 {@code system:session:manage} 才允许。
     */
    @PostMapping("/admin/sessions/{accountId}/logout")
    public R<Integer> forceLogout(@PathVariable Long accountId,
                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        Claims claims = accessClaims(authorization);
        if (claims == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录状态已失效");
        }
        AuthUser operator = accountClient.principal(Long.valueOf(claims.getSubject()));
        boolean allowed = operator.isSuperAdmin()
                || (operator.getPermissions() != null && operator.getPermissions().contains(PERM_SESSION_MANAGE));
        if (!allowed) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权强制下线其他账号");
        }
        int count = sessionService.forceLogout(accountId);
        log.info("强制下线 operator={} target={} sessions={}", operator.getAccountId(), accountId, count);
        return R.ok(count);
    }

    // ---------------- 内部工具 ----------------

    private SessionInfo toSessionInfo(AuthUser principal, List<AppBrief> apps) {
        SessionInfo info = new SessionInfo();
        info.setAccountId(principal.getAccountId());
        info.setUsername(principal.getUsername());
        info.setNickname(principal.getNickname());
        info.setSuperAdmin(principal.isSuperAdmin());
        info.setPermissions(principal.getPermissions());
        info.setApps(principal.getApps());
        info.setAppList(apps);
        return info;
    }

    /** 解析 Bearer 里的 access claims；无效返回 null（调用方决定是否报未登录） */
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
