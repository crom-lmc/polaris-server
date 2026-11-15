package com.laimengchao.sso.ehr.client.api;

import com.laimengchao.sso.ehr.client.config.EhrFeignConfig;
import com.laimengchao.sso.ehr.client.dto.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * EHR 组织维度的服务间 API（原 sso-account EhrOrgClient 的 RestTemplate 调用收敛到这里）。
 */
@FeignClient(
        name = "ehr",
        contextId = "ehr-org-client",
        url = "${ehr.client.base-url:${EHR_URL:http://127.0.0.1:9006}}",
        configuration = EhrFeignConfig.class)
public interface EhrOrgApi {

    /** 该组织及所有下级组织下的账号 ID（按字符串下发，避免雪花 ID 丢精度） */
    @GetMapping("/internal/orgs/{id}/account-ids")
    R<List<String>> accountIds(@PathVariable("id") Long orgId);

    /** 该组织编码（scopeCode LIKE 前缀）下所有员工对应的账号 ID（数据权限引擎用） */
    @GetMapping("/internal/orgs/account-ids-by-code")
    R<List<String>> accountIdsByCode(@RequestParam String scopeCode);
}
