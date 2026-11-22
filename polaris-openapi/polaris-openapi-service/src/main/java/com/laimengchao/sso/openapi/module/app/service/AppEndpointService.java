package com.laimengchao.sso.openapi.module.app.service;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.api.OpenapiAccountApi;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.app.vo.AppEndpointVo;
import com.laimengchao.sso.openapi.module.endpoint.entity.ApiOpenEndpoint;
import com.laimengchao.sso.openapi.module.endpoint.service.EndpointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 第三方对接应用可调用接口清单：按应用域下的开放 API 权限码匹配 endpoint 目录。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppEndpointService {

    private static final String APP_TYPE_OPENAPI_CLIENT = "OPENAPI_CLIENT";
    private static final String PLATFORM_NAME = "统一开放平台";

    private final AccountApi accountApi;
    private final OpenapiAccountApi openapiAccountApi;
    private final EndpointService endpointService;

    /** 取指定第三方应用被授权调用的接口清单（含方向文案）。 */
    public List<AppEndpointVo> listByApp(String appCode) {
        if (appCode == null || appCode.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "appCode 必填");
        }

        R<List<String>> permResp;
        R<List<AppInfo>> appResp;
        try {
            permResp = openapiAccountApi.appPermissions(appCode);
            appResp = accountApi.listEnabledAppsByType(APP_TYPE_OPENAPI_CLIENT);
        } catch (Exception e) {
            log.error("查询对接应用接口清单失败：appCode={}", appCode, e);
            throw new BizException(OpenapiErrorCode.CHANNEL_ERROR, "查询对接应用接口清单失败");
        }
        if (permResp == null || !permResp.ok()) {
            throw new BizException(ErrorCode.BIZ_ERROR, "查询应用权限码失败");
        }
        List<String> perms = permResp.getData() == null ? Collections.emptyList() : permResp.getData();
        Set<String> permSet = new HashSet<>(perms);

        String appName = appCode;
        if (appResp != null && appResp.ok() && appResp.getData() != null) {
            for (AppInfo info : appResp.getData()) {
                if (appCode.equals(info.getCode()) && info.getName() != null && !info.getName().isBlank()) {
                    appName = info.getName();
                    break;
                }
            }
        }

        final String name = appName;
        return endpointService.list().stream()
                .filter(ep -> ep.getRequiredPerm() != null && !ep.getRequiredPerm().isBlank())
                .filter(ep -> permSet.contains(ep.getRequiredPerm()))
                .map(ep -> toVo(ep, name))
                .toList();
    }

    private AppEndpointVo toVo(ApiOpenEndpoint ep, String appName) {
        AppEndpointVo vo = new AppEndpointVo();
        vo.setApiCode(ep.getApiCode());
        vo.setApiName(ep.getApiName());
        vo.setDirection(ep.getDirection());
        boolean inbound = ep.getDirection() == null || ep.getDirection() == 1;
        // 业务语义按用户定义：对外提供=出（底层 INBOUND），调用第三方=进（底层 OUTBOUND）
        vo.setDirectionLabel(inbound ? "对外提供" : "调用第三方");
        vo.setDirectionFlow(inbound
                ? appName + " → " + PLATFORM_NAME
                : PLATFORM_NAME + " → " + (ep.getAppCode() == null ? "第三方系统" : ep.getAppCode()));
        vo.setOperationType(ep.getOperationType());
        vo.setMethod(ep.getMethod());
        vo.setPath(ep.getPath());
        vo.setAppCode(ep.getAppCode());
        vo.setRequiredPerm(ep.getRequiredPerm());
        vo.setStatus(ep.getStatus());
        return vo;
    }
}
