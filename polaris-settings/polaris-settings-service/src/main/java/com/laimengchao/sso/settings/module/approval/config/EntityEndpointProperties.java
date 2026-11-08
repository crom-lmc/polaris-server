package com.laimengchao.sso.settings.module.approval.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批「数据绑定」的<b>应用路由表</b>：appCode → 该应用的对内基址。
 * <p>
 * 为什么需要它：真绑定要按应用去各自的库拉表清单与真实行，而每个应用是独立进程、独立数据库。
 * 早期只有 ehr 提供 /internal/entity/**，代码里写死了一个 ehrBaseUrl；现在 cms / blog /
 * sso-center / settings 都挂上了同一套端点，路由就必须是配置而不是常量——
 * 换环境（本机 → 生产）只改配置，不动代码。
 * <p>
 * 没配的应用（如纯前端的 portal，自身没有数据源）视为「未开通数据绑定」，
 * 前端选到它时给出明确提示，而不是抛一堆连接异常。
 */
@Data
@Component
@ConfigurationProperties(prefix = "approval.entity")
public class EntityEndpointProperties {

    /**
     * appCode → 对内基址，如 {@code ehr: http://127.0.0.1:9006}。
     * key 用应用注册表里的 code（与 cfg_biz_scene.app_code 一致）。
     */
    private Map<String, String> baseUrls = new LinkedHashMap<>();

    /** 按 appCode 取基址（大小写不敏感）；未开通返回 null，由调用方决定怎么提示。 */
    public String find(String appCode) {
        if (appCode == null || appCode.isBlank() || baseUrls == null) {
            return null;
        }
        String url = baseUrls.get(appCode.trim().toLowerCase());
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url.trim();
    }

    /** 已开通数据绑定的应用清单（前端可据此把不可用的应用标灰） */
    public Map<String, String> available() {
        Map<String, String> out = new LinkedHashMap<>();
        if (baseUrls != null) {
            baseUrls.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    out.put(k, v);
                }
            });
        }
        return out;
    }
}
