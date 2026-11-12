package com.laimengchao.sso.client.api;

import com.laimengchao.sso.client.config.SsoInternalFeignConfig;
import com.laimengchao.sso.client.dto.AccountAuthRequest;
import com.laimengchao.sso.client.dto.AccountStatusRequest;
import com.laimengchao.sso.client.dto.AccountUser;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.client.dto.AppVerifyRequest;
import com.laimengchao.sso.client.dto.Contact;
import com.laimengchao.sso.client.dto.EmailCodeRequest;
import com.laimengchao.sso.client.dto.EmailVerifyRequest;
import com.laimengchao.sso.client.dto.OpenAccountRequest;
import com.laimengchao.sso.client.dto.OpenAccountResult;
import com.laimengchao.sso.client.dto.PasswordResetRequest;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.client.dto.WechatOpenidRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * sso-account 的服务间 API（原 ehr AccountClient、auth AccountClient、settings AppsController 的 RestClient 调用统一收敛到这里）。
 * 统一由 {@link SsoInternalFeignConfig} 注入 X-Internal-Token 与超时。
 */
@FeignClient(
        name = "sso-account",
        contextId = "sso-account-client",
        url = "${sso.client.account-base-url:${SSO_ACCOUNT_URL:http://127.0.0.1:9002}}",
        configuration = SsoInternalFeignConfig.class)
public interface AccountApi {

    /** 幂等开户：登录名/密码可留空，由账户域生成；命中已有账号时补齐应用与角色授权 */
    @PostMapping("/internal/accounts/open")
    R<OpenAccountResult> open(@RequestBody OpenAccountRequest request);

    /** 启停账号（人员离职/复职联动） */
    @PostMapping("/internal/accounts/{id}/status")
    R<Void> changeStatus(@PathVariable("id") Long accountId, @RequestBody AccountStatusRequest request);

    /** 服务间凭据校验 */
    @PostMapping("/internal/accounts/authenticate")
    R<AccountUser> authenticate(@RequestBody AccountAuthRequest request);

    /** 按账号 ID 取身份快照（登录态续期/权限刷新），app 缺省为 sso */
    @GetMapping("/internal/accounts/{id}/principal")
    R<AccountUser> principal(@PathVariable("id") Long accountId,
                             @RequestParam(value = "app", required = false) String app);

    /** 批量按 ID 取联系人（昵称/邮箱），报警接收人等场景用 */
    @GetMapping("/internal/accounts/contacts")
    R<List<Contact>> contacts(@RequestParam("ids") String ids);

    /** 微信 openid 反查账号 ID，未绑定返回 null */
    @PostMapping("/internal/accounts/wechat/by-openid")
    R<Long> accountIdByOpenid(@RequestBody WechatOpenidRequest request);

    /** 校验邮箱验证码，成功返回账号 ID */
    @PostMapping("/internal/accounts/email/verify")
    R<Long> verifyEmail(@RequestBody EmailVerifyRequest request);

    /** 生成邮箱验证码，返回发送结果描述 */
    @PostMapping("/internal/accounts/email/code")
    R<String> createEmailCode(@RequestBody EmailCodeRequest request);

    /** 邮箱验证码校验通过后重置密码（不校验原密码，调用方负责验证码校验） */
    @PostMapping("/internal/accounts/password/reset")
    R<Void> resetPassword(@RequestBody PasswordResetRequest request);

    /** 该账号已授权/可访问的应用 */
    @GetMapping("/internal/accounts/{id}/apps")
    R<List<AppInfo>> apps(@PathVariable("id") Long accountId);

    /** 平台所有启用中的应用（下拉选项用） */
    @GetMapping("/internal/apps")
    R<List<AppInfo>> listEnabledApps();

    /** 应用凭据校验 */
    @PostMapping("/internal/apps/verify")
    R<AppInfo> verifyApp(@RequestBody AppVerifyRequest request);
}
