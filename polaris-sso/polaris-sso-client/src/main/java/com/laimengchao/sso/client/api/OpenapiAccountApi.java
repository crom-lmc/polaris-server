package com.laimengchao.sso.client.api;

import com.laimengchao.sso.client.config.SsoInternalFeignConfig;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.client.dto.SignatureVerifyRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 接口开放平台 → 统一登录中心的专用 Feign 客户端。
 * <p>
 * 与 {@link AccountApi} 共用 {@link SsoInternalFeignConfig}（X-Internal-Token + 超时），
 * 单独拆一个 contextId 是为了不与既有客户端的配置上下文冲突。
 * 两个方法分别解决「第三方权限从哪来」和「签名密钥不扩散」：
 * <ul>
 *   <li>{@link #appPermissions(String)}：取应用注册的开放 API 权限码；</li>
 *   <li>{@link #verifySignature(SignatureVerifyRequest)}：由账号中心用 app_secret 重算 HMAC 验签。</li>
 * </ul>
 */
@FeignClient(
        name = "sso-account",
        contextId = "sso-account-openapi-client",
        url = "${sso.client.account-base-url:${SSO_ACCOUNT_URL:http://127.0.0.1:9002}}",
        configuration = SsoInternalFeignConfig.class)
public interface OpenapiAccountApi {

    @GetMapping("/internal/openapi/app-permissions")
    R<List<String>> appPermissions(@RequestParam("appCode") String appCode);

    @PostMapping("/internal/openapi/verify-signature")
    R<Boolean> verifySignature(@RequestBody SignatureVerifyRequest request);
}
