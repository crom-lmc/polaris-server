package com.laimengchao.sso.blog.security;

import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.utils.AccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把 sso-gateway 注入的可信头翻译成登录态。
 * <p>
 * 这是本服务唯一「认证」入口，也是它和原来单体博客最大的区别：
 * <ul>
 *   <li><b>不验签</b>：JWT 的验签发生在网关，验完才写这些头；</li>
 *   <li><b>不查库</b>：权限码随令牌下发，直接信任；</li>
 *   <li><b>没有任何登录接口</b>：密码、验证码、锁定全部交给账号中心。</li>
 * </ul>
 * 安全前提：9003 只能在内网可达。一旦直接暴露公网，任何人都能伪造
 * {@code X-Account-Id} 冒充管理员 —— 网关虽然在入口剥离了同名头，
 * 但那管不到绕过网关的直连请求。
 */
@Slf4j
@Component
public class SsoHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            buildAuthentication(request);
            chain.doFilter(request, response);
        } finally {
            // 线程复用：不清理会把上一个请求的身份带给下一个请求
            SecurityContextHolder.clearContext();
            AccountContext.clear();
        }
    }

    private void buildAuthentication(HttpServletRequest request) {
        String rawId = request.getHeader(JwtClaims.Headers.ACCOUNT_ID);
        if (rawId == null || rawId.isBlank()) {
            return;
        }
        Long accountId;
        try {
            accountId = Long.valueOf(rawId.trim());
        } catch (NumberFormatException e) {
            // 头被串改：当作未登录，后续鉴权会给 401
            log.warn("非法的账户标识头：{}", rawId);
            return;
        }

        String username = nvl(request.getHeader(JwtClaims.Headers.USERNAME));
        boolean superAdmin = "true".equalsIgnoreCase(request.getHeader(JwtClaims.Headers.SUPER_ADMIN));
        String appCode = nvl(request.getHeader(JwtClaims.Headers.APP_CODE));
        Set<String> permissions = split(request.getHeader(JwtClaims.Headers.PERMISSIONS));

        SecurityUser principal = SecurityUser.of(accountId, username, superAdmin, appCode, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        AccountContext.set(new AccountContext.AccountInfo(
                accountId, username, permissions, superAdmin, appCode));
    }

    private Set<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    private String nvl(String s) {
        return s == null ? "" : s.trim();
    }
}
