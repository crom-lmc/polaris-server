package com.laimengchao.sso.ehr.security;

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
 * EHR 原来没有任何 {@code /internal/**} 端点，请求一律走网关注入的可信头；现在多了两类
 * 服务间调用（settings 解析组织负责人、settings 回传审批结果），它们不带用户令牌，
 * 所以必须在 Security 之外单独把关——内网不等于可信，直连 9006 的进程也要先对上暗号。
 * <p>
 * 语义与 sso-account / settings 的同类拦截器完全一致：共用同一个 {@code SSO_INTERNAL_TOKEN}，
 * 三处实现刻意重复而不抽公共模块，是为了让每个服务的准入策略能被单独审阅。
 */
@Component
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final String expected;

    public InternalTokenInterceptor(@Value("${ehr.internal-token:}") String expected) {
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
