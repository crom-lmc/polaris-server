package com.laimengchao.sso.settings.module.security.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.constant.PermissionCodes;
import com.laimengchao.sso.settings.module.security.dto.SecurityConfigRequest;
import com.laimengchao.sso.settings.module.security.entity.SecurityConfig;
import com.laimengchao.sso.settings.module.security.service.SecurityConfigService;
import com.laimengchao.sso.settings.security.Permit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 管理端安全配置接口（网关后，settings-web 使用）。
 * 查看需 security:view，保存需 security:manage（页面按钮同样按此控制显隐）。
 */
@Slf4j
@RestController
@RequestMapping("/api/settings/security")
public class AdminSecurityController {

    private final SecurityConfigService securityConfigService;

    public AdminSecurityController(SecurityConfigService securityConfigService) {
        this.securityConfigService = securityConfigService;
    }

    @GetMapping
    @Permit(PermissionCodes.SECURITY_VIEW)
    public R<SecurityConfig> get() {
        return R.ok(securityConfigService.get());
    }

    @PutMapping
    @Permit(PermissionCodes.SECURITY_MANAGE)
    public R<SecurityConfig> save(@Valid @RequestBody SecurityConfigRequest req) {
        return R.ok(securityConfigService.save(req));
    }
}
