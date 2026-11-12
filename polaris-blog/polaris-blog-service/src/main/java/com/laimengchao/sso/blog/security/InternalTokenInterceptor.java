package com.laimengchao.sso.blog.security;

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
 * 服务间准入校验：{@code /internal/**} 必须携带正确的 X-Internal-Token。
 * <p>
 * 博客新增的 {@code /internal/entity/**}（审批配置绑表时的表清单 / 列元数据 / 取数）
 * 由配置中心在服务端调用，请求不带用户令牌（不走网关、没有 X-Account-* 头），
 * 所以除在 Security 里放行外，还必须单独对暗号——内网不等于可信。
 * <p>
 * 与 ehr / settings / cms / sso-account 的实现刻意重复：每个服务的准入策略要能被单独审阅。
 */
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final String expected;

    public InternalTokenInterceptor(@Value("${blog.internal-token:}") String expected) {
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
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                JsonUtils.toJson(R.fail(ErrorCode.UNAUTHORIZED, "内部调用凭据无效")));
        return false;
    }
}
