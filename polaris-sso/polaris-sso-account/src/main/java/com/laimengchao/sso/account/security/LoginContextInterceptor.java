package com.laimengchao.sso.account.security;

import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.utils.AccountContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 请求入口拦截器：从网关注入的可信头填充 {@link AccountContext}，请求结束时清除。
 * <p>
 * 信任前提：这些头只可能由 sso-gateway 写入——网关已验签 JWT 并**先剥离**客户端
 * 自带的同名头。若服务端口直接暴露公网，任何人都能伪造 X-Account-Id 冒充管理员，
 * 因此 account 只能在内网可达（见 README 的网络边界说明）。
 * <p>
 * 本服务不做验签：JWT 的验证发生在网关，account 只消费结论。这样下游服务无需
 * 各自引入 JWT 库与公钥，权限判断只依赖 {@link AccountContext}。
 */
@Component
@RequiredArgsConstructor
public class LoginContextInterceptor implements HandlerInterceptor {

    private final AccountContextService accountContextService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String rawId = request.getHeader(JwtClaims.Headers.ACCOUNT_ID);
        if (rawId == null || rawId.isBlank()) {
            return true;
        }
        try {
            Long accountId = Long.valueOf(rawId.trim());
            String username = request.getHeader(JwtClaims.Headers.USERNAME);
            boolean superAdmin = "true".equalsIgnoreCase(request.getHeader(JwtClaims.Headers.SUPER_ADMIN));
            Set<String> permissions = splitPermissions(request.getHeader(JwtClaims.Headers.PERMISSIONS));
            // 令牌作用域由网关按 JWT 的 app 声明注入，客户端改不了（网关先剥离同名头）
            String appCode = request.getHeader(JwtClaims.Headers.APP_CODE);
            accountContextService.fillFromHeaders(accountId, username, superAdmin, permissions, appCode);
        } catch (NumberFormatException ignored) {
            // 头被串改：当作未登录，后续 @Permit 会判 401
        }
        return true;
    }

    private Set<String> splitPermissions(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AccountContext.clear();
    }
}
