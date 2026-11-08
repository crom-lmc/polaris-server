package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.account.domain.entity.SsoLoginLog;
import com.laimengchao.sso.account.dto.request.CreateAccountRequest;
import com.laimengchao.sso.account.dto.request.ResetPasswordRequest;
import com.laimengchao.sso.account.dto.request.SetAccountAppsRequest;
import com.laimengchao.sso.account.dto.request.UpdateAccountRequest;
import com.laimengchao.sso.account.dto.response.AccountVO;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.security.Permit;
import com.laimengchao.sso.account.service.AccountAppService;
import com.laimengchao.sso.account.service.AccountService;
import com.laimengchao.sso.account.service.LoginLogService;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账户管理接口。每个写操作对应一个按钮级权限码（@Permit）。
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final LoginLogService loginLogService;
    private final AccountAppService accountAppService;

    @GetMapping
    @Permit(PermissionConstants.ACCOUNT_LIST)
    public R<PageResult<AccountVO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId) {
        return R.ok(accountService.page(page, size, keyword, orgId));
    }

    @GetMapping("/{id}")
    @Permit(PermissionConstants.ACCOUNT_LIST)
    public R<AccountVO> detail(@PathVariable Long id) {
        return R.ok(accountService.detail(id));
    }

    @PostMapping
    @Permit(PermissionConstants.ACCOUNT_CREATE)
    public R<AccountVO> create(@RequestBody CreateAccountRequest req) {
        return R.ok(accountService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionConstants.ACCOUNT_EDIT)
    public R<AccountVO> update(@PathVariable Long id, @RequestBody UpdateAccountRequest req) {
        return R.ok(accountService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionConstants.ACCOUNT_DELETE)
    public R<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return R.ok();
    }

    @GetMapping("/login-logs")
    @Permit(PermissionConstants.ACCOUNT_LOG_VIEW)
    public R<PageResult<SsoLoginLog>> loginLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String loginType) {
        return R.ok(loginLogService.page(page, size, keyword, loginType));
    }

    @PostMapping("/{id}/enable")
    @Permit(PermissionConstants.ACCOUNT_ENABLE)
    public R<Void> enable(@PathVariable Long id) {
        accountService.enable(id);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    @Permit(PermissionConstants.ACCOUNT_DISABLE)
    public R<Void> disable(@PathVariable Long id) {
        accountService.disable(id);
        return R.ok();
    }

    @PostMapping("/{id}/unlock")
    @Permit(PermissionConstants.ACCOUNT_UNLOCK)
    public R<Void> unlock(@PathVariable Long id) {
        accountService.unlock(id);
        return R.ok();
    }

    @PostMapping("/{id}/password")
    @Permit(PermissionConstants.ACCOUNT_RESET_PWD)
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordRequest req) {
        accountService.resetPassword(id, req);
        return R.ok();
    }

    // ---------------- 账号的应用授权 ----------------

    /** 该账号已授权的应用 ID（含停用应用，便于前端回显勾选） */
    @GetMapping("/{id}/apps")
    @Permit(PermissionConstants.ACCOUNT_LIST)
    public R<List<String>> appIds(@PathVariable Long id) {
        // 应用ID也是雪花值，转字符串返回，否则前端勾选回显时对不上（19 位数字超 JS 安全整数）
        return R.ok(accountAppService.appIds(id).stream().map(String::valueOf).toList());
    }

    /** 该账号可访问的应用详情（仅启用） */
    @GetMapping("/{id}/apps/detail")
    @Permit(PermissionConstants.ACCOUNT_LIST)
    public R<List<ApplicationVO>> apps(@PathVariable Long id) {
        return R.ok(accountAppService.appsOf(id));
    }

    /** 覆盖式设置可访问应用：传空数组即收回全部 */
    @PutMapping("/{id}/apps")
    @Permit(PermissionConstants.ACCOUNT_APP_ASSIGN)
    public R<Void> grantApps(@PathVariable Long id, @RequestBody SetAccountAppsRequest req) {
        accountAppService.grant(id, req.getAppIds());
        return R.ok();
    }
}
