package com.laimengchao.sso.openapi.module.endpoint.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.service.EndpointService;
import com.laimengchao.sso.openapi.security.admin.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 开放 API 目录管理接口（SSO 用户令牌 + @Permit）。 */
@RestController
@RequestMapping("/api/open/admin/endpoint")
@RequiredArgsConstructor
public class EndpointAdminController {

    private final EndpointService endpointService;

    @GetMapping("/list")
    @Permit(PermissionCodes.OPENAPI_ENDPOINT_LIST)
    public R<List<ApiOpenEndpoint>> list() {
        return R.ok(endpointService.list());
    }

    @PostMapping("/save")
    @Permit({PermissionCodes.OPENAPI_ENDPOINT_CREATE, PermissionCodes.OPENAPI_ENDPOINT_EDIT})
    public R<Void> save(@RequestBody ApiOpenEndpoint req) {
        endpointService.save(req);
        return R.ok();
    }

    @PostMapping("/{apiCode}/toggle")
    @Permit(PermissionCodes.OPENAPI_ENDPOINT_TOGGLE)
    public R<Void> toggle(@PathVariable String apiCode, @RequestParam("status") int status) {
        endpointService.toggle(apiCode, status);
        return R.ok();
    }

    @DeleteMapping("/{apiCode}")
    @Permit(PermissionCodes.OPENAPI_ENDPOINT_DELETE)
    public R<Void> delete(@PathVariable String apiCode) {
        endpointService.delete(apiCode);
        return R.ok();
    }
}
