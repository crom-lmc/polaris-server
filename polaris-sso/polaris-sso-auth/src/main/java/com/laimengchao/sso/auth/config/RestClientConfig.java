package com.laimengchao.sso.auth.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 仅剩 WechatService（第三方微信开放平台，非内部服务）使用的直连 RestTemplate。
 * <p>
 * sso-account 的服务间调用已全部迁到 polaris-sso-client 的 Feign 接口，
 * 原先为 {@code lb://sso-account} 服务的 {@code lbRestTemplate} 已随之删除。
 */
@Configuration
public class RestClientConfig {

    @Bean
    @Primary
    public RestTemplate plainRestTemplate(RestTemplateBuilder builder, AuthProperties props) {
        Duration timeout = Duration.ofMillis(props.getAccountTimeoutMillis());
        RestTemplate rt = builder.setConnectTimeout(timeout).setReadTimeout(timeout).build();
        rt.setRequestFactory(new BufferingClientHttpRequestFactory(rt.getRequestFactory()));
        return rt;
    }
}
