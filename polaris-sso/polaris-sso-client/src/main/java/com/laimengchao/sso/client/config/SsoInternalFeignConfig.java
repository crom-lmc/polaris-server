package com.laimengchao.sso.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * {@link com.laimengchao.sso.client.api.AccountApi} 专用的 Feign 配置
 * （经 @FeignClient#configuration 引入，不是全局配置，故不标 @Configuration）。
 * <p>
 * 1. 所有请求自动带 X-Internal-Token——与 sso-account 的 InternalTokenInterceptor 约定一致；
 * 2. 连接 3s、读 10s：开户/鉴权是普通内部调用，但开户会写库并补授权，读超时比纯查询放宽一些。
 */
public class SsoInternalFeignConfig {

    @Value("${sso.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor ssoInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options ssoAccountRequestOptions() {
        return new Request.Options(3_000, 10_000, true);
    }
}
