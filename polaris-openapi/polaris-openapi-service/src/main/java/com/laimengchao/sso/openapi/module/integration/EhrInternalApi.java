package com.laimengchao.sso.openapi.module.integration;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.openapi.config.InternalFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/** EHR 人力中心 internal 接口：组织树、人员、组织成员直连（X-Internal-Token 准入）。 */
@FeignClient(
        name = "ehr-internal",
        contextId = "openapi-ehr-internal",
        url = "${internal.ehr-url:http://127.0.0.1:9006}",
        configuration = InternalFeignConfig.class)
public interface EhrInternalApi {

    /** 全量组织节点（扁平，id/parentId 为字符串） */
    @GetMapping("/internal/orgs/tree")
    R<List<Map<String, Object>>> orgTree();

    /** 已开户未离职人员（id=账号ID，含姓名/工号/orgId） */
    @GetMapping("/internal/orgs/employees/active")
    R<List<Map<String, Object>>> activeEmployees();

    /** 某组织直属已开户成员 */
    @GetMapping("/internal/orgs/{id}/employees")
    R<List<Map<String, Object>>> orgEmployees(@PathVariable("id") Long orgId);
}
