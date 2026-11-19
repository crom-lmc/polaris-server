package com.laimengchao.sso.forum.security;

import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.utils.AccountContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 将网关验签后注入的可信账号头转换为 Spring Security 登录态。 */
@Component
public class SsoHeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            String rawId = request.getHeader(JwtClaims.Headers.ACCOUNT_ID);
            if (rawId != null && !rawId.isBlank()) {
                try {
                    Long accountId = Long.valueOf(rawId.trim());
                    String username = nvl(request.getHeader(JwtClaims.Headers.USERNAME));
                    boolean superAdmin = "true".equalsIgnoreCase(request.getHeader(JwtClaims.Headers.SUPER_ADMIN));
                    String appCode = nvl(request.getHeader(JwtClaims.Headers.APP_CODE));
                    SecurityUser principal = SecurityUser.of(accountId, username, superAdmin, appCode,
                            split(request.getHeader(JwtClaims.Headers.PERMISSIONS)));
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                    AccountContext.set(new AccountContext.AccountInfo(
                            accountId, username, principal.getPermissions(), superAdmin, appCode));
                } catch (NumberFormatException ignored) {
                    SecurityContextHolder.clearContext();
                }
            }
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
            AccountContext.clear();
        }
    }

    private Set<String> split(String raw) {
        if (raw == null || raw.isBlank()) return Set.of();
        return Arrays.stream(raw.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    private String nvl(String value) {
        return value == null ? "" : value.trim();
    }
}
