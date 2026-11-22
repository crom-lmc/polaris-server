package com.laimengchao.sso.openapi.module.setting.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.setting.entity.ApiOpenSetting;
import com.laimengchao.sso.openapi.module.setting.service.SettingService;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 平台设置管理接口（SSO 用户令牌 + @Permit）。 */
@RestController
@RequestMapping("/api/open/admin/setting")
@RequiredArgsConstructor
public class SettingAdminController {

    private final SettingService settingService;

    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_SETTING_EDIT)
    public R<List<ApiOpenSetting>> list() {
        return R.ok(settingService.list());
    }

    /** 保存单个配置项：{@code {"key":"ip.whitelist","value":"1.2.3.4\n10.0.0.0/8"}} */
    @PostMapping("/save")
    @Permit(PermissionCodes.OPENAPI_SETTING_EDIT)
    public R<Void> save(@RequestBody Map<String, String> body) {
        String key = body.get("key");
        if (key == null || key.isBlank()) {
            throw new com.laimengchao.sso.common.BizException(
                    com.laimengchao.sso.common.ErrorCode.PARAM_ERROR, "key 必填");
        }
        settingService.save(key, body.get("value"), body.get("remark"));
        return R.ok();
    }
}
