package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.dto.request.InternalAuthenticateRequest;
import com.laimengchao.sso.account.dto.request.InternalAccountStatusRequest;
import com.laimengchao.sso.account.dto.request.InternalCreateCodeRequest;
import com.laimengchao.sso.account.dto.request.InternalOpenAccountRequest;
import com.laimengchao.sso.account.dto.request.InternalResetPasswordRequest;
import com.laimengchao.sso.account.dto.request.InternalVerifyEmailRequest;
import com.laimengchao.sso.account.dto.request.InternalWechatRequest;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.dto.response.LoginResult;
import com.laimengchao.sso.account.dto.response.OpenAccountResult;
import com.laimengchao.sso.account.service.AccountAppService;
import com.laimengchao.sso.account.service.AccountService;
import com.laimengchao.sso.account.service.ApplicationService;
import com.laimengchao.sso.account.service.EmailCodeService;
import com.laimengchao.sso.account.service.LoginLogService;
import com.laimengchao.sso.account.service.WechatBindService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间内部接口。**不经过网关**，仅供 sso-auth 直连调用，由 X-Internal-Token 拦截器把关。
 * <p>
 * 为什么登录校验放在 account 而不是 auth：密码哈希、失败计数、锁定策略都属于账户域，
 * auth 只负责「拿到结论后签发令牌」，这样账户规则只有一处实现。
 */
@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountController {

    private final AccountService accountService;
    private final LoginLogService loginLogService;
    private final WechatBindService wechatBindService;
    private final EmailCodeService emailCodeService;
    private final AccountAppService accountAppService;
    private final ApplicationService applicationService;

    /** 校验用户名密码，成功返回身份+权限码；失败抛 401/403。同时落登录审计日志。 */
    @PostMapping("/authenticate")
    public R<LoginResult> authenticate(@Valid @RequestBody InternalAuthenticateRequest req) {
        String ip = req.getClientIp();
        String ua = req.getUserAgent();
        String type = req.getLoginType() == null ? "password" : req.getLoginType();
        Long accountId = null;
        boolean ok = false;
        String reason = null;
        try {
            var account = accountService.authenticate(req.getUsername(), req.getPassword());
            accountId = account.getId();
            ok = true;
            loginLogService.record(req.getUsername(), accountId, type, true, null, ip, ua);
            return R.ok(accountService.buildLoginResult(accountId, req.getAppCode()));
        } catch (BizException e) {
            reason = e.getMessage();
            throw e;
        } finally {
            if (!ok) {
                loginLogService.record(req.getUsername(), null, type, false, reason, ip, ua);
            }
        }
    }

    /** 按 ID 取身份快照，供刷新令牌时拉取最新权限。app 缺省为平台自身。 */
    @GetMapping("/{id}/principal")
    public R<LoginResult> principal(@PathVariable Long id,
                                         @RequestParam(required = false) String app) {
        return R.ok(accountService.principal(id, app));
    }

    /** 联系人信息（邮箱），供设置中心的待办/消息邮件通道取收件人；查不到返回空 data */
    @GetMapping("/{id}/contact")
    public R<Contact> contact(@PathVariable Long id) {
        try {
            var account = accountService.detail(id);
            Contact c = new Contact();
            c.setAccountId(account.getId());
            c.setUsername(account.getUsername());
            c.setNickname(account.getNickname());
            c.setEmail(account.getEmail());
            return R.ok(c);
        } catch (BizException e) {
            return R.ok();
        }
    }

    @lombok.Data
    public static class Contact {
        private Long accountId;
        private String username;
        private String nickname;
        private String email;
    }

    /** 按 ID 取可访问应用详情（auth 组装会话快照用，超管返回全部启用应用）。 */
    @GetMapping("/{id}/apps")
    public R<List<ApplicationVO>> apps(@PathVariable Long id) {
        LoginResult principal = accountService.principal(id);
        if (principal.isSuperAdmin()) {
            return R.ok(applicationService.listEnabled());
        }
        return R.ok(accountAppService.appsOf(id));
    }

    /** 微信登录：按 openid 反查绑定账户ID；未绑定返回 null（auth 引导先绑定）。 */
    @PostMapping("/wechat/by-openid")
    public R<Long> accountIdByOpenid(@RequestBody InternalWechatRequest req) {
        return R.ok(wechatBindService.findAccountIdByOpenid(req.getOpenid(), req.getAppType()));
    }

    /** 邮箱验证码登录：校验通过后返回可登录的账户ID。 */
    @PostMapping("/email/verify")
    public R<Long> verifyEmail(@Valid @RequestBody InternalVerifyEmailRequest req) {
        return R.ok(emailCodeService.verify(req.getEmail(), req.getScene(), req.getCode()));
    }

    /** 生成并保存邮箱验证码，返回明文（由 auth 负责发送邮件）。 */
    @PostMapping("/email/code")
    public R<String> createEmailCode(@RequestBody InternalCreateCodeRequest req) {
        String code = emailCodeService.create(req.getEmail(), req.getScene(), req.getIp());
        return R.ok(code);
    }

    /** 忘记密码：auth 已完成「reset」场景验证码校验，这里只按账号重置密码。 */
    @PostMapping("/password/reset")
    public R<Void> resetPassword(@Valid @RequestBody InternalResetPasswordRequest req) {
        accountService.resetPasswordByAccount(req.getAccountId(), req.getNewPassword());
        return R.ok();
    }

    /**
     * 服务间开户：业务应用（EHR）创建人员时调用。
     * 幂等——同名/同邮箱已存在则复用并补授权；初始密码仅在本次新建时返回。
     */
    @PostMapping("/open")
    public R<OpenAccountResult> open(@Valid @RequestBody InternalOpenAccountRequest req) {
        return R.ok(accountService.openAccount(req));
    }

    /**
     * 服务间启停账号：EHR 办离职时停用、复职时启用。
     * 走内部令牌而不是 @Permit，是因为调用方是另一个服务，没有平台权限码。
     */
    @PostMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody InternalAccountStatusRequest req) {
        accountService.setEnabled(id, req.getEnabled());
        return R.ok();
    }
}
