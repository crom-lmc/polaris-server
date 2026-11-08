package com.laimengchao.sso.settings.module.coderule.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.module.coderule.service.CodeRuleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨服务取号（X-Internal-Token 准入，不经网关）。
 * <p>
 * 业务方在生成数据前调用，把返回的单号写进自己的单据字段，例如：
 * <pre>
 * GET /internal/code-rules/next?tableName=appr_instance
 * </pre>
 * 未配置规则时返回空串；规则由配置中心「单号规则」维护。
 */
@RestController
@RequestMapping("/internal/code-rules")
public class InternalCodeRuleController {

    private final CodeRuleService codeRuleService;

    public InternalCodeRuleController(CodeRuleService codeRuleService) {
        this.codeRuleService = codeRuleService;
    }

    @GetMapping("/next")
    public R<String> next(@RequestParam String tableName) {
        return R.ok(codeRuleService.nextCode(tableName));
    }
}
