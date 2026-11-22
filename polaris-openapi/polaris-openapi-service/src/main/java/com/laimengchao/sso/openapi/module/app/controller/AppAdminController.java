package com.laimengchao.sso.openapi.module.app.controller;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.app.service.AppEndpointService;
import com.laimengchao.sso.openapi.module.app.vo.AppEndpointVo;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 对接应用管理接口（SSO 用户令牌 + @Permit）。
 * <p>
 * 应用的创建/授权/密钥重置/启停继续在统一登录中心完成（无第三方自助门户），
 * 这里只列出来便于开放平台管理端查看；列表来源于 sso-account 的内部接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/open/admin/app")
@RequiredArgsConstructor
public class AppAdminController {

    /** 第三方对接应用在统一登录中心的应用类型 */
    private static final String APP_TYPE_OPENAPI_CLIENT = "OPENAPI_CLIENT";

    private final AccountApi accountApi;
    private final AppEndpointService appEndpointService;

    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_APP_LIST)
    public com.laimengchao.sso.common.R<List<AppInfo>> list() {
        R<List<AppInfo>> resp;
        try {
            resp = accountApi.listEnabledAppsByType(APP_TYPE_OPENAPI_CLIENT);
        } catch (Exception e) {
            log.error("查询对接应用列表失败", e);
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR, "查询对接应用列表失败");
        }
        if (resp == null || !resp.ok()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "查询对接应用列表失败");
        }
        return com.laimengchao.sso.common.R.ok(resp.getData());
    }

    /** 指定第三方应用被授权调用的接口清单（按该应用域下的 inbound:/outbound: 权限码匹配） */
    @GetMapping("/{appCode}/endpoints")
    @Permit(PermissionCodes.OPENAPI_APP_LIST)
    public com.laimengchao.sso.common.R<List<AppEndpointVo>> endpoints(@PathVariable String appCode) {
        return com.laimengchao.sso.common.R.ok(appEndpointService.listByApp(appCode));
    }
}
