package com.laimengchao.sso.portal.controller;

import com.laimengchao.sso.client.api.AuthApi;
import com.laimengchao.sso.client.dto.TicketExchangeRequest;
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

/**
 * 单点登录的最后一跳：把一次性 ticket / OAuth2 授权码换成 portal 作用域的令牌。
 * appSecret 只在本服务，前端只经手 60 秒一次性票据；两个端点都必须匿名可访问。
 */
@Slf4j
@RestController
@RequestMapping("/api/portal/sso")
@RequiredArgsConstructor
public class SsoExchangeController {

    private final AuthApi authApi;

    @Value("${sso.portal.app-code:portal}")
    private String appCode;

    @Value("${sso.portal.app-secret:}")
    private String appSecret;

    /** ticket 换令牌（cookie 单点流） */
    @PostMapping("/exchange")
    public R<Map<String, Object>> exchange(@RequestBody Map<String, String> body) {
        String ticket = body == null ? null : body.get("ticket");
        if (ticket == null || ticket.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "ticket 不能为空");
        }
        requireSecret();
        try {
            com.laimengchao.sso.client.dto.R<Map<String, Object>> resp =
                    authApi.ticketExchange(ticketRequest(ticket));
            if (resp == null || !resp.ok()) {
                String msg = resp == null || resp.getMessage() == null ? "认证服务无响应" : resp.getMessage();
                // 上游认证拒绝（ticket/code 无效、过期、重放等）：按业务错误返 200 + code!=0，
                // 不能用 401——前端会把它当「登录过期」触发重新授权死循环
                throw new BizException(ErrorCode.BIZ_ERROR, msg);
            }
            return R.ok(resp.getData());
        } catch (BizException e) {
            throw e;
        } catch (FeignException e) {
            log.error("换取令牌失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "认证服务暂不可用，请稍后再试");
        }
    }

    /** OAuth2 标准授权码换令牌（A 档）：auth 返回裸 OAuth2 JSON，这里做 snake→camel 映射 */
    @PostMapping("/oauth/exchange")
    public R<Map<String, Object>> oauthExchange(@RequestBody Map<String, String> body) {
        String code = body == null ? null : body.get("code");
        String redirectUri = body == null ? null : body.get("redirectUri");
        if (code == null || code.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "code 不能为空");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "redirectUri 不能为空");
        }
        requireSecret();
        try {
            Map<String, Object> resp = authApi.oauthToken(oauthForm(code, redirectUri));
            if (resp == null || resp.containsKey("error")) {
                String msg = resp == null ? "认证服务无响应"
                        : String.valueOf(resp.getOrDefault("error_description", resp.get("error")));
                throw new BizException(ErrorCode.BIZ_ERROR, msg);
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
            throw new BizException(ErrorCode.BIZ_ERROR, e.displayMessage());
        } catch (FeignException e) {
            log.error("OAuth2 换码失败", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "认证服务暂不可用，请稍后再试");
        }
    }

    private TicketExchangeRequest ticketRequest(String ticket) {
        TicketExchangeRequest req = new TicketExchangeRequest();
        req.setTicket(ticket);
        req.setAppCode(appCode);
        req.setAppSecret(appSecret);
        return req;
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

    private void requireSecret() {
        if (appSecret == null || appSecret.isBlank()) {
            log.error("未配置 sso.portal.app-secret，无法完成单点换票");
            throw new BizException(ErrorCode.SYSTEM_ERROR, "单点登录未配置应用密钥");
        }
    }
}
