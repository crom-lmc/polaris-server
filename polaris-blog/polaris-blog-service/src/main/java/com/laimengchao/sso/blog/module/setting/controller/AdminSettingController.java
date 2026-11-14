package com.laimengchao.sso.blog.module.setting.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.blog.common.log.OpLog;
import com.laimengchao.sso.blog.module.setting.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.laimengchao.sso.blog.security.Permit;
import com.laimengchao.sso.common.security.PermissionCodes;

/**
 * 后台站点设置（仅管理员）。
 */
@RestController
@RequestMapping("/api/blog/admin/settings")
@RequiredArgsConstructor
@Tag(name = "后台-站点设置", description = "基础信息 / SEO / 评论策略 / 友链 / 社交链接")
public class AdminSettingController {

    private final SettingService settingService;

    @GetMapping
    @Operation(summary = "获取全部站点设置")
    @Permit(PermissionCodes.SETTING_VIEW)
    public R<Map<String, String>> all() {
        return R.ok(settingService.all());
    }

    @PutMapping
    @OpLog(module = "setting", action = "save")
    @Operation(summary = "批量保存站点设置", description = "传入 KV Map，缺失的 key 不会被动删除")
    @Permit(PermissionCodes.SETTING_EDIT)
    public R<Void> save(@RequestBody Map<String, String> values) {
        settingService.save(values);
        return R.ok();
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新站点设置缓存")
    @Permit(PermissionCodes.SETTING_EDIT)
    public R<Void> refresh() {
        settingService.refresh();
        return R.ok();
    }
}
