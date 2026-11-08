package com.laimengchao.sso.client.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.client.exception.AuthOAuthException;
import feign.codec.ErrorDecoder;
import feign.Request;
import feign.Util;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

/**
 * {@link com.laimengchao.sso.client.api.AuthApi} 专用的 Feign 配置。
 * <p>
 * 换票端点是匿名公开接口，不需要 X-Internal-Token；但 OAuth2 token 端点用裸 JSON
 * 返回错误（{@code {error, error_description}}），不是统一 R 壳，默认 Feign 的
 * ErrorDecoder 会把 4xx 抛成 FeignException 并丢掉协议语义。这里把错误体解析出来，
 * 交给调用方按原样透传 error_description。
 */
public class AuthFeignConfig {

    @Bean
    public Request.Options ssoAuthRequestOptions() {
        return new Request.Options(3_000, 10_000, true);
    }

    @Bean
    public ErrorDecoder ssoAuthOAuthErrorDecoder(ObjectMapper objectMapper) {
        return (methodKey, response) -> {
            if (response.status() >= 400 && response.status() < 500 && response.body() != null) {
                try {
                    String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
                    if (!body.isBlank()) {
                        JsonNode node = objectMapper.readTree(body);
                        String error = node.path("error").asText(null);
                        String description = node.path("error_description").asText(null);
                        return new AuthOAuthException(response.status(), error, description);
                    }
                } catch (Exception ignored) {
                    // 解析失败则退回 Feign 默认错误处理
                }
            }
            return new ErrorDecoder.Default().decode(methodKey, response);
        };
    }
}
