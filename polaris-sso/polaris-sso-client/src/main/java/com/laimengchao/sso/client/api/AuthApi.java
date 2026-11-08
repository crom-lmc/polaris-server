package com.laimengchao.sso.client.api;

import com.laimengchao.sso.client.config.AuthFeignConfig;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.client.dto.TicketExchangeRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * sso-auth 的服务端换票 API（原 5 个应用 SsoExchangeController 的 RestClient 调用统一收敛到这里）。
 * 换票端点是匿名公开接口，不注入 X-Internal-Token，改由各应用自己的 appCode + appSecret 证明身份。
 */
@FeignClient(
        name = "sso-auth",
        contextId = "sso-auth-client",
        url = "${sso.client.auth-base-url:${SSO_AUTH_URL:http://127.0.0.1:9001}}",
        configuration = AuthFeignConfig.class)
public interface AuthApi {

    /** 一次性票据换令牌，返回 R 壳 */
    @PostMapping("/api/auth/ticket/exchange")
    R<Map<String, Object>> ticketExchange(@RequestBody TicketExchangeRequest request);

    /**
     * OAuth2 标准 token 端点，必须 application/x-www-form-urlencoded，成功返回裸 OAuth2 JSON；
     * 失败由 {@link AuthFeignConfig} 的 ErrorDecoder 解析成 {@code AuthOAuthException}。
     * 请求体用 {@link com.laimengchao.sso.client.support.OAuthForm#encode} 手工编码，
     * 不要改用 MultiValueMap（Feign 会按 JSON 编码或发空体）。
     */
    @PostMapping(value = "/api/auth/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    Map<String, Object> oauthToken(@RequestBody String form);
}
