package com.laimengchao.sso.forum.controller;

import com.laimengchao.sso.client.api.AuthApi;
import com.laimengchao.sso.client.exception.AuthOAuthException;
import com.laimengchao.sso.client.support.OAuthForm;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/forum/sso")
@RequiredArgsConstructor
public class SsoExchangeController {

    private final AuthApi authApi;

    @Value("${sso.forum.app-code:forum}")
    private String appCode;

    @Value("${sso.forum.app-secret:}")
    private String appSecret;

    @Value("${sso.forum.admin-app-code:forum-admin}")
    private String adminAppCode;

    @Value("${sso.forum.admin-app-secret:}")
    private String adminAppSecret;

    @PostMapping("/oauth/exchange")
    public R<Map<String, Object>> oauthExchange(@RequestBody Map<String, String> body) {
        return exchange(body, appCode, appSecret, "sso.forum.app-secret");
    }

    @PostMapping("/oauth/admin-exchange")
    public R<Map<String, Object>> adminExchange(@RequestBody Map<String, String> body) {
        return exchange(body, adminAppCode, adminAppSecret, "sso.forum.admin-app-secret");
    }

    private R<Map<String, Object>> exchange(Map<String, String> body, String clientId,
                                            String clientSecret, String secretConfigKey) {
        String code = body == null ? null : body.get("code");
        String redirectUri = body == null ? null : body.get("redirectUri");
        if (code == null || code.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "code 不能为空");
        if (redirectUri == null || redirectUri.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "redirectUri 不能为空");
        if (clientSecret == null || clientSecret.isBlank()) {
            log.error("未配置 {}，无法完成单点换票", secretConfigKey);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "单点登录未配置应用密钥");
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("redirect_uri", redirectUri);
        try {
            Map<String, Object> resp = authApi.oauthToken(OAuthForm.encode(form));
            if (resp == null || resp.containsKey("error")) {
                String msg = resp == null ? "认证服务无响应"
                        : String.valueOf(resp.getOrDefault("error_description", resp.get("error")));
                throw new BizException(ErrorCode.UNAUTHORIZED, msg);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            if (resp.containsKey("access_token")) out.put("accessToken", resp.get("access_token"));
            if (resp.containsKey("refresh_token")) out.put("refreshToken", resp.get("refresh_token"));
            if (resp.containsKey("token_type")) out.put("tokenType", resp.get("token_type"));
            if (resp.containsKey("expires_in")) out.put("expiresIn", resp.get("expires_in"));
            if (resp.containsKey("scope")) out.put("scope", resp.get("scope"));
            return R.ok(out);
        } catch (BizException e) {
            throw e;
        } catch (AuthOAuthException e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, e.displayMessage());
        } catch (FeignException e) {
            log.error("论坛 OAuth2 换码失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "认证服务暂不可用，请稍后再试");
        }
    }
}
