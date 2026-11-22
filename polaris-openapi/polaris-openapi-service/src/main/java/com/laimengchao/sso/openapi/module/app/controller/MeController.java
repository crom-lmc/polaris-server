package com.laimengchao.sso.openapi.module.app.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.openapi.security.admin.PermissionAspect;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理端当前用户（前端渲染菜单/按钮权限用）。
 * 经网关访问，登录态由 SsoHeaderAuthFilter 注入；仅 openapi 作用域令牌可用。
 */
@RestController
@RequestMapping("/api/open/admin")
@RequiredArgsConstructor
public class MeController {

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!PermissionAspect.APP_CODE.equals(user.getAppCode())) {
            throw new BizException(ErrorCode.FORBIDDEN, "该接口仅限接口开放平台令牌访问");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accountId", user.getAccountId());
        out.put("username", user.getUsername());
        out.put("superAdmin", user.isSuperAdmin());
        out.put("appCode", user.getAppCode());
        out.put("permissions", user.getPermissions());
        return R.ok(out);
    }
}
