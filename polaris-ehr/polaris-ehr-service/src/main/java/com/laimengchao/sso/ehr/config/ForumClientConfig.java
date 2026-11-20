package com.laimengchao.sso.ehr.config;

import com.laimengchao.sso.ehr.integration.ForumMemberApi;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * 注册开户联动论坛成员的服务间 Feign 客户端。
 * <p>
 * 与 ehr-client / sso-client 的自动配置各自独立，这里只多注册一个 ForumMemberApi，
 * 地址键 {@code ehr.forum.base-url}，令牌复用 {@code SSO_INTERNAL_TOKEN}。
 */
@Configuration
@EnableFeignClients(clients = {ForumMemberApi.class})
public class ForumClientConfig {
}
