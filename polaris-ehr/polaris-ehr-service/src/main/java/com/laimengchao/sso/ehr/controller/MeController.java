package com.laimengchao.sso.ehr.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.security.SecurityUser;
import com.laimengchao.sso.ehr.security.SecurityUtils;
import lombok.Data;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * 当前登录身份快照：前端拿到 cms 令牌后调它刷新用户与权限（无 @Permit，登录即可）。
 */
@RestController
@RequestMapping("/api/ehr")
public class MeController {

    @GetMapping("/me")
    public R<Me> me() {
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new com.laimengchao.sso.common.BizException(
                        com.laimengchao.sso.common.ErrorCode.UNAUTHORIZED));
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
        /** 雪花 ID 转字符串：前端拿 number 会丢精度，和 localStorage 里的账号比对会永远不相等 */
        @com.fasterxml.jackson.databind.annotation.JsonSerialize(
                using = com.fasterxml.jackson.databind.ser.std.ToStringSerializer.class)
        private Long accountId;
        private String username;
        private boolean superAdmin;
        private String appCode;
        private Set<String> permissions;
    }
}
