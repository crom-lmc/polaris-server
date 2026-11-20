package com.laimengchao.sso.ehr.integration;

import com.laimengchao.sso.client.config.SsoInternalFeignConfig;
import com.laimengchao.sso.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 论坛成员的服务间 API：开户成功后把账号登记为论坛成员（幂等）。
 * <p>
 * 走直连地址（不经网关），复用 {@link SsoInternalFeignConfig} 自动注入 X-Internal-Token，
 * 与论坛 /internal/** 的 InternalTokenInterceptor 对齐。
 */
@FeignClient(
        name = "forum-member",
        contextId = "forum-member-client",
        url = "${ehr.forum.base-url:${FORUM_URL:http://127.0.0.1:9009}}",
        configuration = SsoInternalFeignConfig.class)
public interface ForumMemberApi {

    /** 幂等登记成员：重复投递不会覆盖已有论坛资料 */
    @PostMapping("/internal/forum/members/ensure")
    R<Void> ensure(@RequestBody ForumMemberRequest request);
}
