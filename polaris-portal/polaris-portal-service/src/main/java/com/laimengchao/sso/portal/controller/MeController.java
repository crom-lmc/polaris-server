package com.laimengchao.sso.portal.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.portal.common.PortalErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * 当前登录身份快照：前端拿到令牌后调它刷新用户（无 @Permit，登录即可）。
 * 门户目前主要用于展示登录态；评论等需登录功能后续基于此扩展。
 */
@RestController
@RequestMapping("/api/portal")
public class MeController {

    @GetMapping("/me")
    public R<Me> me() {
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        Me me = new Me();
        me.setAccountId(user.getAccountId());
        me.setUsername(user.getUsername());
        me.setSuperAdmin(user.isSuperAdmin());
        me.setAppCode(user.getAppCode());
        me.setPermissions(user.getPermissions());
        return R.ok(me);
    }

    @Data
    public static class Me {
        private Long accountId;
        private String username;
        private boolean superAdmin;
        private String appCode;
        private Set<String> permissions;
    }
}
