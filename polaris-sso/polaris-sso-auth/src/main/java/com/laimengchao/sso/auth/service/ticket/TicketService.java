package com.laimengchao.sso.auth.service.ticket;

import com.laimengchao.sso.auth.config.AuthProperties;
import com.laimengchao.sso.auth.dto.AppBrief;
import com.laimengchao.sso.auth.dto.TicketResponse;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * 一次性票据的签发与消费。
 * <p>
 * 单点的完整链路：
 * <pre>
 * 应用A 发现未登录 → 302 到 SSO /login?app=A&redirect=A的接收地址
 * SSO 发现已有中心会话 → POST /api/auth/ticket → 拿到 ticket
 * 302 回 A 的接收地址?ticket=xxx
 * A 后端 → POST /api/auth/ticket/exchange（带 appSecret）→ 换回 JWT
 * </pre>
 * 票据只是「一次性通行证」，真正的身份仍由 JWT 表达；这样应用侧不用共享会话存储。
 */
@Slf4j
@Service
public class TicketService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final TicketStore ticketStore;
    private final AuthProperties props;

    public TicketService(TicketStore ticketStore, AuthProperties props) {
        this.ticketStore = ticketStore;
        this.props = props;
    }

    /**
     * 签发票据。
     *
     * @param myApps 当前账号可访问的应用清单——不在清单里直接 403，这是「账号能访问哪些应用」的落点
     */
    public TicketResponse issue(Long accountId, String appCode, String redirect, List<AppBrief> myApps) {
        return issue(accountId, appCode, redirect, myApps, null);
    }

    /**
     * 签发票据。
     *
     * @param myApps 当前账号可访问的应用清单——不在清单里直接 403，这是「账号能访问哪些应用」的落点
     * @param sid    发起换票时所在的中心会话，会被写进票据，最终落到换出的令牌上，
     *               这样应用侧拿到的令牌与 SSO 会话同生共死
     */
    public TicketResponse issue(Long accountId, String appCode, String redirect,
                                List<AppBrief> myApps, String sid) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        AppBrief target = myApps == null ? null : myApps.stream()
                .filter(a -> appCode.equals(a.getCode()))
                .findFirst().orElse(null);
        if (target == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前账号无权访问该应用：" + appCode);
        }

        String ticket = randomTicket();
        ticketStore.save(ticket, new TicketStore.TicketPayload(accountId, appCode, sid),
                props.getTicketTtlSeconds());

        String redirectTo = resolveRedirect(redirect, target.getBaseUrl(), ticket);
        return new TicketResponse(ticket, appCode, target.getName(), redirectTo, props.getTicketTtlSeconds());
    }

    /** 消费票据：取走即删，重复使用拿不到（返回 null） */
    public TicketStore.TicketPayload take(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        return ticketStore.take(ticket);
    }

    /**
     * 决定最终跳转地址。
     * <p>
     * 安全要点：{@code redirect} 来自 URL 参数，绝不能无条件信任——否则 SSO 就成了
     * 开放重定向跳板，攻击者能拿它把用户导到钓鱼站。严格模式下只接受与应用
     * baseUrl 同 origin 的地址，否则回落到应用注册的 baseUrl。
     */
    private String resolveRedirect(String redirect, String baseUrl, String ticket) {
        String target = baseUrl == null ? "" : baseUrl.trim();
        if (redirect != null && !redirect.isBlank()) {
            if (isAllowedRedirect(redirect.trim(), target)) {
                target = redirect.trim();
            } else {
                log.warn("拒绝不合法的回跳地址：{}（期望 origin 属于 {}）", redirect, target);
            }
        }
        if (target.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "该应用未配置访问地址，无法跳转，请先在应用管理中填写 baseUrl");
        }
        String sep = target.contains("?") ? "&" : "?";
        return target + sep + "ticket=" + URLEncoder.encode(ticket, StandardCharsets.UTF_8);
    }

    /** 同 origin（协议 + 主机 + 端口）才放行；baseUrl 为空时只允许相对路径 */
    private boolean isAllowedRedirect(String redirect, String baseUrl) {
        if (!props.isTicketStrictRedirect()) {
            return true;
        }
        boolean relative = redirect.startsWith("/") && !redirect.startsWith("//");
        if (baseUrl.isBlank()) {
            return relative;
        }
        if (relative) {
            return true;
        }
        try {
            URI want = URI.create(redirect);
            URI allowed = URI.create(baseUrl);
            return equalsIgnoreCase(want.getScheme(), allowed.getScheme())
                    && equalsIgnoreCase(want.getHost(), allowed.getHost())
                    && effectivePort(want) == effectivePort(allowed);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean equalsIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b == null ? "" : b);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private String randomTicket() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
