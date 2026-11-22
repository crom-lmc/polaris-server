package com.laimengchao.sso.openapi.module.integration;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.config.InternalFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/** 配置中心 internal 接口：主数据与字典直连（X-Internal-Token 准入）。 */
@FeignClient(
        name = "settings-internal",
        contextId = "openapi-settings-internal",
        url = "${internal.settings-url:http://127.0.0.1:9007}",
        configuration = InternalFeignConfig.class)
public interface SettingsInternalApi {

    @GetMapping("/internal/biz/categories")
    R<?> bizCategories();

    @GetMapping("/internal/biz/{type}")
    R<List<Map<String, Object>>> bizItems(@PathVariable("type") String type);

    @GetMapping("/internal/dicts/{typeCode}")
    R<List<Map<String, Object>>> dictItems(@PathVariable("typeCode") String typeCode);

    @PostMapping("/internal/dicts/batch")
    R<Map<String, List<Map<String, Object>>>> dictBatch(@RequestBody Map<String, Object> body);
}
