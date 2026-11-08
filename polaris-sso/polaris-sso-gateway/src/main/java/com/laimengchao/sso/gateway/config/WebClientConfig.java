package com.laimengchao.sso.gateway.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 两个 WebClient.Builder，按目标地址形态二选一：
 * <ul>
 *   <li>{@code plainWebClientBuilder}：普通直连，用于 {@code http://127.0.0.1:9001}</li>
 *   <li>{@code lbWebClientBuilder}：带客户端负载均衡，用于 {@code lb://sso-auth}</li>
 * </ul>
 * 与 sso-auth 的 RestClientConfig 同一思路：只挂一个 @LoadBalanced 会把 127.0.0.1
 * 当成「服务名」去注册中心查，查不到直接报错，所以直连必须走普通实例。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder plainWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @LoadBalanced
    public WebClient.Builder lbWebClientBuilder() {
        return WebClient.builder();
    }
}
