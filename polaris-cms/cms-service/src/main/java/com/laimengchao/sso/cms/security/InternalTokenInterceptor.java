package com.laimengchao.sso.cms.security;

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
 * 服务间准入校验：{@code /internal/**} 必须携带正确的 X-Internal-Token。
 * <p>
 * CMS 新增的 {@code /internal/cms/approval-callback} 由 settings 在审批结束后调用，
 * 请求不带用户令牌（不走网关、没有 X-Account-* 头），所以除了在 Security 里放行，
 * 还必须单独对暗号——内网不等于可信，直连 9004 的进程同样要先对上令牌。
 * <p>
 * 与 ehr / settings / sso-account 的实现刻意重复：每个服务的准入策略要能被单独审阅。
 */
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final String expected;

    public InternalTokenInterceptor(@Value("${cms.internal-token:}") String expected) {
        this.expected = expected;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String actual = request.getHeader(JwtClaims.Headers.INTERNAL_TOKEN);
        if (actual != null && !expected.isEmpty() && expected.equals(actual)) {
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
