package com.laimengchao.sso.job.module.job.controller;

import com.laimengchao.sso.client.api.AuthApi;
import com.laimengchao.sso.client.exception.AuthOAuthException;
import com.laimengchao.sso.client.support.OAuthForm;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 授权码换 JWT（前端 → job-center 后端代理 → sso-auth），以及登录后的身份快照。
 * appSecret 只存在后端，前端只传 code，绝不泄露密钥。
 * /exchange 匿名开放（前端刚回来还没有 token），靠授权码一次性有效性兜底；/me 需要带令牌。
 * <p>
 * 换票走与其他应用同一个 {@link AuthApi}（polaris-sso-client），地址键 sso.client.auth-base-url，
 * 失败由 AuthFeignConfig 的 ErrorDecoder 解析成 {@link AuthOAuthException}。
 */
@Slf4j
@RestController
@RequestMapping("/api/jobs/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthApi authApi;

    @Value("${job.sso.app-code:job}")
    private String appCode;

    @Value("${job.sso.app-secret:}")
    private String appSecret;

    @PostMapping("/exchange")
    public R<Map<String, Object>> exchange(@RequestBody ExchangeRequest req) {
        if (req == null || req.code == null || req.code.isBlank()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "code 不能为空");
        }
        if (req.redirectUri == null || req.redirectUri.isBlank()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "redirectUri 不能为空");
        }
        requireSecret();

        try {
            Map<String, Object> resp = authApi.oauthToken(oauthForm(req.code.trim(), req.redirectUri.trim()));
            if (resp == null || resp.containsKey("error")) {
                String msg = resp == null
                        ? "认证服务无响应"
                        : String.valueOf(resp.getOrDefault("error_description", resp.get("error")));
                // 协议级失败按业务错误返 200 + code!=0：返 401 会被前端当成登录过期，形成重授权死循环
                throw new BizException(ErrorCode.BIZ_ERROR, msg);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            copy(resp, out, "access_token", "accessToken");
            copy(resp, out, "refresh_token", "refreshToken");
            copy(resp, out, "token_type", "tokenType");
            copy(resp, out, "expires_in", "expiresIn");
            copy(resp, out, "scope", "scope");
            return R.ok(out);
        } catch (BizException e) {
            throw e;
        } catch (AuthOAuthException e) {
            // 授权码无效/过期/已使用等：同样是业务错误，不能冒泡成 500
            throw new BizException(ErrorCode.BIZ_ERROR, e.displayMessage());
        } catch (FeignException e) {
            log.error("OAuth2 换码失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "认证服务暂不可用，请稍后再试");
        }
    }

    /** 当前登录身份快照：前端拿到令牌后调它刷新用户与权限（无 @Permit，登录即可） */
    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SecurityUser user)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountId", user.getAccountId());
        out.put("username", user.getUsername());
        out.put("superAdmin", user.isSuperAdmin());
        out.put("appCode", user.getAppCode());
        out.put("permissions", user.getPermissions());
        return R.ok(out);
    }

    private String oauthForm(String code, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("client_id", appCode);
        form.put("client_secret", appSecret);
        form.put("redirect_uri", redirectUri);
        return OAuthForm.encode(form);
    }

    private void copy(Map<String, Object> src, Map<String, Object> dst, String from, String to) {
        if (src.containsKey(from)) {
            dst.put(to, src.get(from));
        }
    }

    private void requireSecret() {
        if (appSecret == null || appSecret.isBlank()) {
            log.error("未配置 job.sso.app-secret（环境变量 JOB_APP_SECRET，见 polaris-nacos/.secrets/app-secrets.env），无法完成单点换票");
            throw new BizException(ErrorCode.BIZ_ERROR, "调度中心未配置应用密钥");
        }
    }

    @lombok.Data
    public static class ExchangeRequest {
        private String code;
        private String redirectUri;
    }
}
