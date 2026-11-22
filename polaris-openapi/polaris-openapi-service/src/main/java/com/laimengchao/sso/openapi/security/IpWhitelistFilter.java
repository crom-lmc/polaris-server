package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.utils.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 第三方来源 IP 白名单过滤器（P2）：对换令牌与所有 v1 开放接口生效。
 * <p>
 * 管理端（用户令牌）、出站（内部服务）、actuator 不受此限。Order=2，在 OpenapiAuthFilter(1) 之后。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class IpWhitelistFilter extends OncePerRequestFilter {

    private final IpWhitelist ipWhitelist;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/open/admin/")
                || path.startsWith("/api/open/outbound/")
                || path.startsWith("/api/open/sso/")
                || path.startsWith("/actuator/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            ipWhitelist.check(clientIp(request));
            chain.doFilter(request, response);
        } catch (BizException e) {
            response.setStatus(403);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(JsonUtils.toJson(R.fail(e.getCode(), e.getMessage())));
        }
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return req.getRemoteAddr();
    }
}
