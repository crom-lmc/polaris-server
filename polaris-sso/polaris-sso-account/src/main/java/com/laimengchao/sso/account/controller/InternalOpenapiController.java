package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.domain.entity.SsoApplication;
import com.laimengchao.sso.account.dto.request.InternalSignatureVerifyRequest;
import com.laimengchao.sso.account.service.ApplicationService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/**
 * 接口开放平台专用的服务间接口。**不经网关**，由 X-Internal-Token 拦截器把关。
 * <p>
 * 的存在意义是让第三方应用的「凭证 + 权限」单一来源留在统一登录中心：
 * <ul>
 *   <li>{@link #appPermissions(String)}：拿某应用在其 app_code 域下注册的开放 API 权限码，
 *       供开放平台签发短期令牌时写入 perms；</li>
 *   <li>{@link #verifySignature(InternalSignatureVerifyRequest)}：用库里的 app_secret 重算 HMAC，
 *       <b>密钥全程不出账号域</b>——开放平台只转发签名材料，拿不到 secret。</li>
 * </ul>
 * 第三方入驻 = 在现有「应用管理」建应用（拿 app_code/app_secret）+ 在现有「权限管理」
 * 给该 app_code 域勾选 inbound 与 outbound 开头的权限码，两步都在统一登录中心完成。
 */
@RestController
@RequestMapping("/internal/openapi")
@RequiredArgsConstructor
public class InternalOpenapiController {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ApplicationService applicationService;

    /** 应用在其 app_code 域下注册的开放 API 权限码（inbound:/outbound: 前缀），供令牌签发写入 perms */
    @GetMapping("/app-permissions")
    public R<List<String>> appPermissions(@RequestParam String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }
        SsoApplication app = applicationService.requireEnabled(appCode);
        List<String> codes = applicationService.apiPermissionCodes(appCode);
        return R.ok(codes);
    }

    /** 用库里的 app_secret 重算 HMAC-SHA256 并定长比对；应用停用或材料不全一律 false */
    @PostMapping("/verify-signature")
    public R<Boolean> verifySignature(@RequestBody InternalSignatureVerifyRequest req) {
        if (req == null || req.getAppCode() == null || req.getAppCode().isBlank()
                || req.getContent() == null || req.getSignature() == null
                || req.getTimestamp() == null || req.getNonce() == null) {
            return R.ok(false);
        }
        SsoApplication app;
        try {
            app = applicationService.requireEnabled(req.getAppCode());
        } catch (BizException e) {
            return R.ok(false);
        }
        if (app.getAppSecret() == null || app.getAppSecret().isBlank()) {
            return R.ok(false);
        }
        try {
            String canonical = req.getTimestamp() + "\n" + req.getNonce() + "\n" + req.getContent();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(app.getAppSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] expected = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(req.getSignature());
            return R.ok(MessageDigest.isEqual(expected, actual));
        } catch (Exception e) {
            // 签名材料非法（如 signature 非 Hex）不作为服务端错误，统一判失败
            return R.ok(false);
        }
    }
}
