package com.laimengchao.sso.openapi.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 下游 internal 接口的 Feign 配置（经 @FeignClient#configuration 引入，不标 @Configuration）：
 * 统一注入 X-Internal-Token，并收紧超时——只读查询，避免下游故障拖垮开放平台线程。
 */
public class InternalFeignConfig {

    @Value("${internal.token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor openapiInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options openapiInternalRequestOptions() {
        return new Request.Options(3_000, 5_000, true);
    }
}
