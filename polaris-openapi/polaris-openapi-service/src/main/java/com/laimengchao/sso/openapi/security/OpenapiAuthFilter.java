package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.security.dto.CallerIdentity;
import com.laimengchao.sso.utils.JsonUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
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
 * 开放接口统一鉴权过滤器：Bearer 短期令牌 或 HMAC 签名二选一。
 * <p>
 * 鉴权通过后把 {@link CallerIdentity} 放进 request attribute，供 controller 取用；
 * 失败统一返回 {@link R} 结构（业务码 + 对应 HTTP 状态）。
 * 换令牌端点 /api/open/token 与 actuator 放行。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class OpenapiAuthFilter extends OncePerRequestFilter {

    public static final String ATTR_IDENTITY = "openapi.identity";
    private static final String TOKEN_PATH = "/api/open/token";
    /** P1 出站接口：走 X-Internal-Token。P2 管理端改为 SSO 用户令牌（见 SsoHeaderAuthFilter），不再走这里 */
    private static final String INTERNAL_OUTBOUND_PREFIX = "/api/open/outbound/";
    /** P2 管理端前缀：由 SsoHeaderAuthFilter + Spring Security + @Permit 处理，本过滤器放行 */
    private static final String ADMIN_PREFIX = "/api/open/admin/";

    private final AccessTokenService accessTokenService;
    private final AuthService authService;
    /** 出站/管理端内部共享密钥，与下游 internal 接口同一把 */
    @org.springframework.beans.factory.annotation.Value("${internal.token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 管理端交给 SsoHeaderAuthFilter + Spring Security + @Permit，本过滤器不再处理
        return path.equals(TOKEN_PATH) || path.startsWith("/actuator/") || path.equals("/error")
                || path.startsWith(ADMIN_PREFIX) || path.startsWith("/api/open/sso/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            // 先包装（内部缓存 body），签名模式读 body 后 controller 仍能重复读取
            CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request);
            CallerIdentity identity = authenticate(wrapped);
            wrapped.setAttribute(ATTR_IDENTITY, identity);
            chain.doFilter(wrapped, response);
        } catch (BizException e) {
            writeError(response, e.getCode(), e.getMessage(), httpStatusOf(e));
        } catch (Exception e) {
            log.error("开放接口鉴权异常", e);
            writeError(response, 5000, "系统异常", 500);
        }
    }

    private CallerIdentity authenticate(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        // P1 出站：X-Internal-Token（服务间调用）。管理端已在 shouldNotFilter 放行。
        if (path.startsWith(INTERNAL_OUTBOUND_PREFIX)) {
            return authenticateInternal(request);
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return accessTokenService.verify(auth.substring(7).trim());
        }
        // 签名模式：把 body 读出来参与签名，并缓存以便 controller 再次读取
        String appCode = request.getHeader("X-App-Code");
        String timestamp = request.getHeader("X-Timestamp");
        String nonce = request.getHeader("X-Nonce");
        String signature = request.getHeader("X-Signature");
        if (appCode != null || signature != null) {
            String body = readBody(request);
            String content = request.getMethod() + "\n" + request.getRequestURI() + "\n" + body;
            return authService.authenticateSignature(appCode, timestamp, nonce, content, signature);
        }
        throw new BizException(com.laimengchao.sso.openapi.common.OpenapiErrorCode.TOKEN_INVALID);
    }

    /** 内部通道鉴权：X-Internal-Token 必须与配置一致；未配置密钥时直接拒绝，避免空口令放行。 */
    private CallerIdentity authenticateInternal(HttpServletRequest request) {
        String token = request.getHeader("X-Internal-Token");
        if (internalToken == null || internalToken.isBlank()
                || token == null || !internalToken.equals(token)) {
            throw new BizException(com.laimengchao.sso.openapi.common.OpenapiErrorCode.INTERNAL_TOKEN_INVALID);
        }
        String app = request.getHeader("X-Internal-App");
        if (app == null || app.isBlank()) {
            app = "internal";
        }
        // 内部调用方已通过共享密钥准入，权限由 endpoint.required_perm + 渠道 allowedApps 二次约束；
        // 这里给全量通配，避免内部服务还要去账号中心注册 openapi 域权限码。
        return new CallerIdentity(app, java.util.Set.of("*"));
    }

    private String readBody(HttpServletRequest request) throws IOException {
        byte[] buf = request.getInputStream().readAllBytes();
        return new String(buf, StandardCharsets.UTF_8);
    }

    private int httpStatusOf(BizException e) {
        return e.getErrorCode() != null && e.getErrorCode().httpStatus() > 0
                ? e.getErrorCode().httpStatus() : 401;
    }

    private void writeError(HttpServletResponse response, int code, String message, int status)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(R.fail(code, message)));
    }

    /** 缓存 body 的 wrapper，保证签名读取后 controller 仍能拿到请求体。 */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        @Override
        public jakarta.servlet.ServletInputStream getInputStream() {
            return new jakarta.servlet.ServletInputStream() {
                private final java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(body);

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(jakarta.servlet.ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(
                    new java.io.InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
