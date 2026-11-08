package com.laimengchao.sso.account.controller;

import com.laimengchao.sso.account.dto.request.ChangePasswordRequest;
import com.laimengchao.sso.account.dto.response.AccountVO;
import com.laimengchao.sso.account.dto.response.ApplicationVO;
import com.laimengchao.sso.account.service.AccountAppService;
import com.laimengchao.sso.account.service.AccountService;
import com.laimengchao.sso.account.service.ApplicationService;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前登录者的账户详情与可访问应用。
 * <p>
 * 登录/登出/签发现在归 sso-auth（{@code /api/auth/**}），本服务只做资源查询。
 * 这里不再需要 @Permit——「看自己的资料」不该要求额外权限，只要登录即可。
 */
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class CurrentAccountController {

    private final AccountService accountService;
    private final AccountAppService accountAppService;
    private final ApplicationService applicationService;

    @GetMapping("/me")
    public R<AccountVO> me() {
        return R.ok(accountService.detail(currentId()));
    }

    /** 我可以用哪些应用：超管视作全部启用应用，与登录时写进 JWT 的 apps 口径一致 */
    @GetMapping("/me/apps")
    public R<List<ApplicationVO>> myApps() {
        Long id = currentId();
        if (AccountContext.isSuperAdmin()) {
            return R.ok(applicationService.listEnabled());
        }
        return R.ok(accountAppService.appsOf(id));
    }

    /** 当前用户修改自己的密码（校验原密码）。成功后前端应执行登出，下次用新密码登录。 */
    @PostMapping("/me/password")
    public R<Void> changeMyPassword(@RequestBody ChangePasswordRequest req) {
        accountService.changeMyPassword(currentId(), req.getOldPassword(), req.getNewPassword());
        return R.ok();
    }

    private Long currentId() {
        Long id = AccountContext.getAccountId();
        if (id == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return id;
    }
}
