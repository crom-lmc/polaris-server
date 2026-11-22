package com.laimengchao.sso.openapi.module.channel.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.channel.service.ChannelService;
import com.laimengchao.sso.openapi.security.admin.Permit;
import com.laimengchao.sso.openapi.module.channel.vo.ChannelSaveRequest;
import com.laimengchao.sso.openapi.module.channel.vo.CredentialSaveRequest;
import jakarta.validation.Valid;
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
import java.util.Map;

/**
 * 出站渠道管理接口（SSO 用户令牌 + @Permit，经网关访问）。
 * 管理端菜单/按钮权限码 {@code openapi:channel:*} 已在 P0 由 sso-account 播种。
 */
@RestController
@RequestMapping("/api/open/admin/channel")
@RequiredArgsConstructor
public class ChannelAdminController {

    private final ChannelService channelService;

    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_CHANNEL_LIST)
    public R<List<?>> list() {
        return R.ok(channelService.list());
    }

    @GetMapping("/{channelCode}")
    @Permit(PermissionCodes.OPENAPI_CHANNEL_LIST)
    public R<?> get(@PathVariable String channelCode) {
        return R.ok(channelService.get(channelCode));
    }

    @PostMapping("/save")
    @Permit(PermissionCodes.OPENAPI_CHANNEL_CREATE)
    public R<Void> save(@Valid @RequestBody ChannelSaveRequest req) {
        channelService.save(req);
        return R.ok();
    }

    @PostMapping("/{channelCode}/credential")
    @Permit(PermissionCodes.OPENAPI_CHANNEL_CREDENTIAL)
    public R<Void> saveCredential(@PathVariable String channelCode,
                                  @RequestBody CredentialSaveRequest req) {
        channelService.saveCredential(channelCode, req.getCredential());
        return R.ok();
    }

    @PostMapping("/{channelCode}/toggle")
    @Permit(PermissionCodes.OPENAPI_CHANNEL_EDIT)
    public R<Void> toggle(@PathVariable String channelCode,
                          @RequestParam("status") int status) {
        channelService.toggle(channelCode, status);
        return R.ok();
    }

    @DeleteMapping("/{channelCode}")
    @Permit(PermissionCodes.OPENAPI_CHANNEL_DELETE)
    public R<Void> delete(@PathVariable String channelCode) {
        channelService.delete(channelCode);
        return R.ok();
    }

    /** 便捷：取渠道已解密凭证的字段名列表（不含值），供管理端确认配置结构，绝不泄露明文。 */
    @GetMapping("/{channelCode}/credential-fields")
    @Permit(PermissionCodes.OPENAPI_CHANNEL_CREDENTIAL)
    public R<Map<String, Object>> credentialFields(@PathVariable String channelCode) {
        Map<String, Object> credential = channelService.resolveCredential(channelCode);
        Map<String, Object> fields = new java.util.LinkedHashMap<>();
        credential.keySet().forEach(k -> fields.put(k, "******"));
        return R.ok(fields);
    }
}
