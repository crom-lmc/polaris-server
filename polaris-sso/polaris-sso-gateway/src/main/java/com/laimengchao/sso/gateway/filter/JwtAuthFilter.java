package com.laimengchao.sso.gateway.filter;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.gateway.config.GatewayProperties;
import com.laimengchao.sso.gateway.support.JwtVerifier;
import com.laimengchao.sso.gateway.support.RevocationCache;
import com.laimengchao.sso.utils.JsonUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一鉴权过滤器。执行顺序：
 * <ol>
 *   <li><b>先剥离</b>客户端自带的 X-Account-* 头——这一步漏了，整套权限就是摆设</li>
 *   <li>拒绝外部访问内部接口前缀</li>
 *   <li>白名单放行（登录/刷新/登出）</li>
 *   <li>验签 + 校验类型 + 查吊销</li>
 *   <li>把身份写回可信头，交给下游</li>
 * </ol>
 * 下游服务因此不需要任何 JWT 依赖，只读头即可。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String AUTHORIZATION = "Authorization";

    private final GatewayProperties props;
    private final JwtVerifier verifier;
    private final RevocationCache revocationCache;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 1) 不管后续走哪条分支，先清掉不可信的身份头
        ServerWebExchange clean = stripTrustedHeaders(exchange);

        // 2) 内部接口只允许服务间内网直连，外部一律 403
        if (props.getInternalPrefixes().stream().anyMatch(path::startsWith)) {
            return deny(clean, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "内部接口不对外开放");
        }

        // 3) 白名单
        if (isWhitelisted(path)) {
            return chain.filter(clean);
        }

        // 4) 取令牌
        String token = bearer(clean.getRequest().getHeaders().getFirst(AUTHORIZATION));
        if (token == null || token.isBlank()) {
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "未登录，请先获取访问令牌");
        }

        Claims claims;
        try {
            claims = verifier.verify(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 验签失败 path={} reason={}", path, e.getMessage());
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "令牌无效或已过期");
        }

        if (!JwtClaims.TYPE_ACCESS.equals(claims.get(JwtClaims.TOKEN_TYPE, String.class))) {
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "该接口需要 access token");
        }
        if (revocationCache.isRevoked(claims.getId()) || isSessionRevoked(claims)) {
            return deny(clean, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }

        // 5) 注入可信头
        // X-App-Code 取令牌里的 app 声明，而不是让调用方自己填：
        // 它决定下游用哪一套权限码，客户端能改就等于能越权
        ServerHttpRequest enriched = clean.getRequest().mutate()
                .header(JwtClaims.Headers.ACCOUNT_ID, nvl(claims.getSubject()))
                .header(JwtClaims.Headers.USERNAME, nvl(claims.get(JwtClaims.USERNAME, String.class)))
                .header(JwtClaims.Headers.SUPER_ADMIN,
                        String.valueOf(Boolean.TRUE.equals(claims.get(JwtClaims.SUPER_ADMIN, Boolean.class))))
                .header(JwtClaims.Headers.APP_CODE, nvl(claims.get(JwtClaims.APP, String.class)))
                .header(JwtClaims.Headers.PERMISSIONS, nvl(claims.get(JwtClaims.PERMISSIONS, String.class)))
                .header(JwtClaims.Headers.APPS, nvl(claims.get(JwtClaims.APPS, String.class)))
                .build();
        return chain.filter(clean.mutate().request(enriched).build());
    }

    /**
     * 白名单判定：精确匹配，或以 {@code /**} 结尾时按前缀匹配。
     * 业务应用的读者端（如 /api/blog/public/**）必须能整段放行，
     * 否则每加一个公开接口都得回来改一次网关。
     */
    private boolean isWhitelisted(String path) {
        for (String pattern : props.getWhitelist()) {
            if (pattern.endsWith("/**")) {
                if (path.startsWith(pattern.substring(0, pattern.length() - 3))) {
                    return true;
                }
            } else if (pattern.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** 撕掉任何客户端自称的身份，杜绝伪造 X-Account-Id 冒充管理员。 */
    private ServerWebExchange stripTrustedHeaders(ServerWebExchange exchange) {
        ServerHttpRequest sanitized = exchange.getRequest().mutate()
                .headers(h -> {
                    h.remove(JwtClaims.Headers.ACCOUNT_ID);
                    h.remove(JwtClaims.Headers.USERNAME);
                    h.remove(JwtClaims.Headers.SUPER_ADMIN);
                    h.remove(JwtClaims.Headers.PERMISSIONS);
                    h.remove(JwtClaims.Headers.APPS);
                    h.remove(JwtClaims.Headers.APP_CODE);
                    h.remove(JwtClaims.Headers.INTERNAL_TOKEN);
                })
                .build();
        return exchange.mutate().request(sanitized).build();
    }

    /**
     * 会话级吊销：同一个浏览器里，别的应用换出来的令牌 jti 各不相同，但 sid 相同。
     * 只查 jti 的话，「A 应用退出、B 应用照常」——这正是本节要解决的问题。
     * 存量令牌没有 sid 声明，此处按未吊销处理，行为与改动前一致。
     */
    private boolean isSessionRevoked(Claims claims) {
        String sid = claims.get(JwtClaims.SESSION_ID, String.class);
        return sid != null && !sid.isBlank()
                && revocationCache.isRevoked(JwtClaims.SID_REVOKE_PREFIX + sid);
    }

    private Mono<Void> deny(ServerWebExchange exchange, HttpStatus status, ErrorCode code, String message) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        byte[] bytes = JsonUtils.toJson(R.fail(code, message)).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private String bearer(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String v = authorization.trim();
        return v.regionMatches(true, 0, "Bearer ", 0, 7) ? v.substring(7).trim() : v;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    /** 必须早于路由过滤器执行 */
    @Override
    public int getOrder() {
        return -100;
    }
}
