package com.laimengchao.sso.settings.client.api;

import com.laimengchao.sso.settings.client.config.ApprovalFeignConfig;
import com.laimengchao.sso.settings.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 单号规则取号接口（/internal/code-rules/**，凭 X-Internal-Token 准入，不经网关）。
 * <p>
 * 业务方在生成数据前调用，把返回的单号写进自己的单据字段；未配置规则时返回空串，
 * 调用方自行决定是否兜底。规则与流水号由配置中心「单号规则」维护。
 */
@FeignClient(
        name = "settings",
        contextId = "settings-code-rule",
        url = "${settings.client.base-url:http://127.0.0.1:9007}",
        configuration = ApprovalFeignConfig.class)
public interface CodeRuleApi {

    /** 按业务表名取下一个单号，如 appr_instance / ehr_resign_apply */
    @GetMapping("/internal/code-rules/next")
    R<String> next(@RequestParam("tableName") String tableName);
}
