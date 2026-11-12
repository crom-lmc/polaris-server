package com.laimengchao.sso.job.security;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.utils.JsonUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 服务间准入校验：/internal/** 必须携带正确的 X-Internal-Token。
 * <p>
 * 与 sso-account / settings 同一套语义（共享同一个 SSO_INTERNAL_TOKEN）：
 * 内网不等于可信，直连 9008 的任何进程都要先对上暗号。令牌为空时拒绝全部请求（fail closed）。
 */
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final String expected;

    public InternalTokenInterceptor(@Value("${job.internal-token:}") String expected) {
        this.expected = expected;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        String actual = request.getHeader(JwtClaims.Headers.INTERNAL_TOKEN);
        if (expected.equals(actual)) {
            return true;
        }
        response.setStatus(ErrorCode.UNAUTHORIZED.getCode());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                JsonUtils.toJson(R.fail(ErrorCode.UNAUTHORIZED, "内部调用凭据无效")));
        return false;
    }
}
