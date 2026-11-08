package com.laimengchao.sso.settings.client.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 审批接口专用的 Feign 配置。
 * <p>
 * 与字典查询不同，审批调用是<b>写操作且可能串起业务回调</b>：settings 审批通过后会同步
 * 回调业务方（等业务把状态改完），链路比一次字典查询长得多。所以读超时放宽到 20s，
 * 而连接超时仍是 3s——连不上说明服务挂了，早点失败比挂着强。
 * <p>
 * 同样要带 X-Internal-Token：/internal/approval/** 与 /internal/dicts/** 用同一把暗号。
 */
public class ApprovalFeignConfig {

    @Value("${settings.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    @Bean
    public RequestInterceptor approvalInternalTokenInterceptor() {
        return template -> {
            if (internalToken != null && !internalToken.isBlank()) {
                template.header("X-Internal-Token", internalToken);
            }
        };
    }

    @Bean
    public Request.Options approvalRequestOptions() {
        return new Request.Options(3_000, 20_000, true);
    }
}
