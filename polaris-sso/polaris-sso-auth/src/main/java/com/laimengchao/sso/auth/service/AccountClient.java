package com.laimengchao.sso.auth.service;

import com.laimengchao.sso.auth.dto.AppBrief;
import com.laimengchao.sso.auth.dto.AuthUser;
import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.AccountAuthRequest;
import com.laimengchao.sso.client.dto.AccountUser;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.client.dto.AppVerifyRequest;
import com.laimengchao.sso.client.dto.EmailCodeRequest;
import com.laimengchao.sso.client.dto.EmailVerifyRequest;
import com.laimengchao.sso.client.dto.PasswordResetRequest;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.client.dto.WechatOpenidRequest;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.utils.JsonUtils;
import feign.FeignException;
import feign.RetryableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * sso-account 的服务间客户端（Feign）。
 * <p>
 * 直连内网地址并携带 {@code X-Internal-Token}——服务间调用不该绕网关（多一跳、
 * 还要伪造一个不存在的用户身份），但必须有自己的准入凭据，否则内网可被随意调用。
 * <p>
 * 本类只做「Feign 调用 + 契约到 auth 内部 DTO 的映射 + 错误语义」，业务层不直接接触 Feign。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClient {

    private final AccountApi accountApi;

    /** 校验凭据；密码错误/账户禁用等由 account 判定，异常原样上抛给前端。 */
    public AuthUser authenticate(String username, String password, String clientIp,
                                 String userAgent, String loginType) {
        return authenticate(username, password, clientIp, userAgent, loginType, com.laimengchao.sso.common.security.JwtClaims.PLATFORM_APP);
    }

    /**
     * 校验凭据，并按目标应用取权限码。
     *
     * @param appCode 权限码所属应用；缺省 sso。业务应用自建登录页时应传自己的 code，
     *                否则签出的令牌会带上平台管理台的按钮权限
     */
    public AuthUser authenticate(String username, String password, String clientIp,
                                 String userAgent, String loginType, String appCode) {
        AccountAuthRequest req = new AccountAuthRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setClientIp(clientIp == null ? "" : clientIp);
        req.setUserAgent(userAgent == null ? "" : userAgent);
        req.setLoginType(loginType == null ? "password" : loginType);
        req.setAppCode(appCode == null || appCode.isBlank()
                ? com.laimengchao.sso.common.security.JwtClaims.PLATFORM_APP : appCode.trim());
        return toAuthUser(unwrap(invoke(() -> accountApi.authenticate(req), "/internal/accounts/authenticate")));
    }

    /** 按账户 ID 取身份快照（刷新令牌时用来刷新权限，保证授权变更即时生效）。 */
    public AuthUser principal(Long accountId) {
        return principal(accountId, com.laimengchao.sso.common.security.JwtClaims.PLATFORM_APP);
    }

    /** 按账户 ID 取指定应用范围内的身份快照：permissions 只含该应用的权限码。 */
    public AuthUser principal(Long accountId, String appCode) {
        String app = appCode == null || appCode.isBlank()
                ? com.laimengchao.sso.common.security.JwtClaims.PLATFORM_APP : appCode.trim();
        return toAuthUser(unwrap(invoke(() -> accountApi.principal(accountId, app),
                "/internal/accounts/" + accountId + "/principal")));
    }

    /** 微信登录：按 openid 反查绑定账户ID；未绑定返回 null。 */
    public Long accountIdByOpenid(String openid, String appType) {
        WechatOpenidRequest req = new WechatOpenidRequest();
        req.setOpenid(openid);
        req.setAppType(appType == null ? "open" : appType);
        R<Long> res = invoke(() -> accountApi.accountIdByOpenid(req), "/internal/accounts/wechat/by-openid");
        return res == null || !res.ok() ? null : res.getData();
    }

    /** 邮箱验证码登录：校验通过后返回可登录的账户ID。 */
    public Long verifyEmail(String email, String scene, String code) {
        EmailVerifyRequest req = new EmailVerifyRequest();
        req.setEmail(email);
        req.setScene(scene == null ? "login" : scene);
        req.setCode(code);
        return unwrap(invoke(() -> accountApi.verifyEmail(req), "/internal/accounts/email/verify"));
    }

    /** 生成并保存邮箱验证码，返回明文（由 auth 发送）。 */
    public String createEmailCode(String email, String scene, String ip) {
        EmailCodeRequest req = new EmailCodeRequest();
        req.setEmail(email);
        req.setScene(scene == null ? "login" : scene);
        req.setIp(ip == null ? "" : ip);
        return unwrap(invoke(() -> accountApi.createEmailCode(req), "/internal/accounts/email/code"));
    }

    /** 邮箱验证码校验通过后重置密码（不校验原密码）。 */
    public void resetPassword(Long accountId, String newPassword, String clientIp, String userAgent) {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setAccountId(accountId);
        req.setNewPassword(newPassword);
        req.setClientIp(clientIp == null ? "" : clientIp);
        req.setUserAgent(userAgent == null ? "" : userAgent);
        R<Void> res = invoke(() -> accountApi.resetPassword(req), "/internal/accounts/password/reset");
        if (res == null || !res.ok()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    res == null ? "重置密码失败" : res.getMessage());
        }
    }

    /** 按账户 ID 取可访问应用详情（组装会话快照用）。 */
    public List<AppBrief> apps(Long accountId) {
        List<AppInfo> apps = unwrap(invoke(() -> accountApi.apps(accountId),
                "/internal/accounts/" + accountId + "/apps"));
        return apps.stream().map(this::toAppBrief).toList();
    }

    /** 平台全部启用应用（待办「应用」筛选用，按账号权限过滤会让所有人看不到全部应用）。 */
    public List<AppBrief> enabledApps() {
        return unwrap(invoke(accountApi::listEnabledApps, "/internal/apps"))
                .stream().map(this::toAppBrief).toList();
    }

    /**
     * 校验应用编码 + 密钥（ticket 换 token 的准入）。
     * <p>返回 false 表示密钥不对或应用已停用——两种情况对外都不区分，避免探测。
     */
    public boolean verifyApp(String code, String secret) {
        AppVerifyRequest req = new AppVerifyRequest();
        req.setCode(code == null ? "" : code);
        req.setSecret(secret == null ? "" : secret);
        try {
            R<AppInfo> res = accountApi.verifyApp(req);
            return res != null && res.ok();
        } catch (RetryableException e) {
            log.error("调用 sso-account 失败: /internal/apps/verify", e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "认证后端不可用，请稍后重试");
        } catch (FeignException e) {
            // 401/403/404 都视为校验失败，不把内部错误细节暴露给调用方
            log.debug("应用密钥校验失败 code={} status={}", code, e.status());
            return false;
        }
    }

    private <T> T invoke(Supplier<T> supplier, String action) {
        try {
            return supplier.get();
        } catch (BizException e) {
            throw e;
        } catch (RetryableException e) {
            log.error("调用 sso-account 失败: {}", action, e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "认证后端不可用，请稍后重试");
        } catch (FeignException e) {
            // account 把「密码错误/账户锁定」映射为 401 或 423，把原始提示透传给前端
            throw new BizException(e.status(), extractMessage(e.status(), e.contentUTF8()));
        }
    }

    private <T> T unwrap(R<T> res) {
        if (res == null || !res.ok() || res.getData() == null) {
            String msg = res == null ? "认证服务返回空响应" : res.getMessage();
            throw new BizException(ErrorCode.UNAUTHORIZED, msg);
        }
        return res.getData();
    }

    private AuthUser toAuthUser(AccountUser user) {
        AuthUser target = new AuthUser();
        target.setAccountId(user.getAccountId());
        target.setUsername(user.getUsername());
        target.setNickname(user.getNickname());
        target.setSuperAdmin(user.isSuperAdmin());
        target.setAppCode(user.getAppCode());
        target.setPermissions(user.getPermissions());
        target.setApps(user.getApps());
        return target;
    }

    private AppBrief toAppBrief(AppInfo info) {
        AppBrief target = new AppBrief();
        target.setId(info.getId());
        target.setCode(info.getCode());
        target.setName(info.getName());
        target.setBaseUrl(info.getBaseUrl());
        target.setRedirectUri(info.getRedirectUri());
        target.setIcon(info.getIcon());
        return target;
    }

    /**
     * 从 account 的错误响应里取具体提示，拿不到时按状态码给清晰文案，杜绝笼统的「认证失败」。
     * <p>401→用户名或密码错误（不区分，避免用户名枚举）；403/423→账户锁定或禁用。
     */
    private String extractMessage(int status, String body) {
        if (body != null && !body.isBlank()) {
            try {
                R<?> r = JsonUtils.fromJson(body, R.class);
                if (r != null && r.getMessage() != null && !r.getMessage().isBlank()) {
                    return r.getMessage();
                }
            } catch (Exception ignored) {
                // 解析失败则走下面的状态码兜底
            }
        }
        if (status == 401) {
            return "用户名或密码错误";
        }
        if (status == 403 || status == 423) {
            return "账户已被锁定或禁用，请联系管理员解锁";
        }
        return "认证失败，请稍后重试";
    }
}
