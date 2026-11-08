package com.laimengchao.sso.account.security;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.JwtClaims;
import com.laimengchao.sso.util.JsonUtils;
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
 * 内网不等于可信——容器网络里任何一个被拿下的服务都能直接打这些接口。
 * 生产应换成 mTLS 或服务网格的 SPIFFE 身份，模板用共享密钥够表达这条边界。
 */
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final String expected;

    public InternalTokenInterceptor(@Value("${sso.account.internal-token}") String expected) {
        this.expected = expected;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String actual = request.getHeader(JwtClaims.Headers.INTERNAL_TOKEN);
        if (actual != null && !expected.isEmpty() && expected.equals(actual)) {
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
