package com.laimengchao.sso.settings.module.coderule.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.settings.constant.PermissionCodes;
import com.laimengchao.sso.settings.module.coderule.entity.CodeRule;
import com.laimengchao.sso.settings.module.coderule.service.CodeRuleService;
import com.laimengchao.sso.settings.security.Permit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端业务单号规则（settings-web 使用）。
 * 查看 code:view，增删改 code:manage。
 */
@RestController
@RequestMapping("/api/settings/code-rules")
public class AdminCodeRuleController {

    private final CodeRuleService codeRuleService;

    public AdminCodeRuleController(CodeRuleService codeRuleService) {
        this.codeRuleService = codeRuleService;
    }

    @GetMapping
    @Permit(PermissionCodes.CODE_VIEW)
    public R<List<CodeRule>> list(@RequestParam(required = false) String keyword) {
        return R.ok(codeRuleService.list(keyword));
    }

    @PostMapping
    @Permit(PermissionCodes.CODE_MANAGE)
    public R<CodeRule> create(@RequestBody CodeRule body) {
        return R.ok(codeRuleService.create(body));
    }

    @PatchMapping("/{id}")
    @Permit(PermissionCodes.CODE_MANAGE)
    public R<CodeRule> update(@PathVariable Long id, @RequestBody CodeRule body) {
        return R.ok(codeRuleService.update(id, body));
    }

    /** 调整「下一个」流水号，只能往后调，防止调到已用号造成重复 */
    @PatchMapping("/{id}/next-no")
    @Permit(PermissionCodes.CODE_MANAGE)
    public R<CodeRule> adjustNextNo(@PathVariable Long id, @RequestParam Integer nextNo) {
        return R.ok(codeRuleService.adjustNextNo(id, nextNo));
    }
}
