package com.laimengchao.sso.ehr.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * {@link com.laimengchao.sso.ehr.client.api.EhrOrgApi} 专用 Feign 配置。
 * 所有请求自动带 X-Internal-Token——与 ehr-service 的 InternalTokenInterceptor 约定一致。
 */
public class EhrFeignConfig {

    @Value("${ehr.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor ehrInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options ehrRequestOptions() {
        return new Request.Options(3_000, 5_000, true);
    }
}
