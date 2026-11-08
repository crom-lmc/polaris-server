package com.laimengchao.sso.settings.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 各 Feign 查询接口（DictApi + 六个主数据 Api）共享的 Feign 配置
 * （经 @FeignClient#configuration 引入，不是全局配置，故不标 @Configuration）。
 * <p>
 * 1. 所有请求自动带 X-Internal-Token——与 settings-service 的 InternalTokenInterceptor 约定一致，
 *    令牌与 sso-account 共用（SSO_INTERNAL_TOKEN），未配置时拦截器空转，调用方会拿到兜底空集合。
 * 2. 显式收紧超时：字典/主数据是简单查询，连接 3s、读 5s 足够，
 *    默认的 60s 读超时会把 settings 的故障放大成调用方的线程堆积。
 */
public class SettingsFeignConfig {

    @Value("${settings.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor settingsInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options settingsRequestOptions() {
        return new Request.Options(3_000, 5_000, true);
    }

}
