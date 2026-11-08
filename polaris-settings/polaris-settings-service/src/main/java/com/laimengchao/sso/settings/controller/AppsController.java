package com.laimengchao.sso.settings.controller;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.AppInfo;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.constant.PermissionCodes;
import com.laimengchao.sso.settings.security.Permit;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用清单（供「归属应用」等下拉选择）。
 * <p>
 * 应用注册表归 sso-account 管，本服务不落库、不缓存到本地表，
 * 经 polaris-sso-client 的 {@link AccountApi} 调 account 内部接口 /internal/apps
 * （不经网关，凭 X-Internal-Token），内存缓存 60 秒避免每次开弹窗都跨服务调用。
 */
@Slf4j
@RestController
@RequestMapping("/api/settings/apps")
@RequiredArgsConstructor
public class AppsController {

    private final AccountApi accountApi;

    @Value("${sso.client.internal-token:${SSO_INTERNAL_TOKEN:}}")
    private String internalToken;

    /** 60s 内存缓存；account 挂掉时退化返回上次成功的结果，保证下拉可用 */
    private volatile List<AppOption> cache;
    private volatile long cacheAt;

    @GetMapping
    @Permit(PermissionCodes.DICT_VIEW)
    public R<List<AppOption>> list() {
        long now = System.currentTimeMillis();
        List<AppOption> snapshot = cache;
        if (snapshot != null && now - cacheAt < 60_000L) {
            return R.ok(snapshot);
        }
        if (internalToken == null || internalToken.isBlank()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "本服务未配置内部调用令牌，无法读取应用列表");
        }
        try {
            com.laimengchao.sso.client.dto.R<List<AppInfo>> resp = accountApi.listEnabledApps();
            if (resp == null || !resp.ok()) {
                String msg = resp == null || resp.getMessage() == null ? "账号中心无响应" : resp.getMessage();
                throw new BizException(ErrorCode.SYSTEM_ERROR, "读取应用列表失败：" + msg);
            }
            List<AppInfo> data = resp.getData();
            List<AppOption> apps = data == null ? List.of() : data.stream()
                    .map(m -> new AppOption(
                            String.valueOf(m.getId()),
                            String.valueOf(m.getCode()),
                            String.valueOf(m.getName())))
                    .toList();
            cache = apps;
            cacheAt = now;
            return R.ok(apps);
        } catch (BizException e) {
            // account 明确报错时，若手上有旧缓存就退化用它，别让弹窗直接不可用
            List<AppOption> fallback = cache;
            if (fallback != null) {
                return R.ok(fallback);
            }
            throw e;
        } catch (Exception e) {
            log.error("调用账号中心 /internal/apps 失败", e);
            List<AppOption> fallback = cache;
            if (fallback != null) {
                return R.ok(fallback);
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR, "账号中心暂不可用，无法读取应用列表");
        }
    }

    /** 应用精简信息（前端下拉用） */
    @Data
    public static class AppOption {
        private final String id;
        private final String code;
        private final String name;
    }
}
