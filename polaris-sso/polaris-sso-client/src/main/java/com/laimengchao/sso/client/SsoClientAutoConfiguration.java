package com.laimengchao.sso.client;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.api.AuthApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 引入 polaris-sso-client 即自动装配统一账号平台的两个跨服 Feign 客户端：
 * <ul>
 *   <li>{@link AccountApi}：{@code /internal/accounts/**}、{@code /internal/apps/**}，凭 X-Internal-Token 准入；</li>
 *   <li>{@link AuthApi}：{@code /api/auth/ticket/exchange}、{@code /api/auth/oauth/token}，由服务端持应用密钥换票。</li>
 * </ul>
 * 地址键：{@code sso.client.account-base-url} / {@code sso.client.auth-base-url}；
 * 令牌键：{@code sso.client.internal-token}（缺省复用 {@code SSO_INTERNAL_TOKEN}）。
 */
@AutoConfiguration
@EnableFeignClients(clients = {AccountApi.class, AuthApi.class})
public class SsoClientAutoConfiguration {
}
