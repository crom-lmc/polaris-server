package com.laimengchao.sso.common.security;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.utils.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 服务间准入校验：{@code /internal/**} 必须携带正确的 X-Internal-Token。
 * <p>
 * 全服务共用同一份实现，由 {@link CommonSecurityWebConfig} 统一注册，避免各服务各写一遍。
 * <p>
 * <b>fail-closed</b>：令牌未配置时拒绝所有内部请求，而不是放行。
 * 内网不等于可信，直连服务端口的进程同样要先对上令牌；配置缺失时必须暴露问题，
 * 不能静默退化成「无鉴权」。
 */
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final byte[] expected;

    public InternalTokenInterceptor(String expected) {
        this.expected = expected == null ? new byte[0]
                : expected.trim().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (expected.length == 0) {
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    ErrorCode.SYSTEM_ERROR, "内部调用令牌未配置（SSO_INTERNAL_TOKEN）");
            return false;
        }
        String actual = request.getHeader(JwtClaims.Headers.INTERNAL_TOKEN);
        if (actual != null && MessageDigest.isEqual(expected,
                actual.trim().getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        write(response, HttpServletResponse.SC_UNAUTHORIZED,
                ErrorCode.UNAUTHORIZED, "内部调用凭据无效");
        return false;
    }

    private void write(HttpServletResponse response, int status, ErrorCode code, String message)
            throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(JsonUtils.toJson(R.fail(code, message)));
    }
}
