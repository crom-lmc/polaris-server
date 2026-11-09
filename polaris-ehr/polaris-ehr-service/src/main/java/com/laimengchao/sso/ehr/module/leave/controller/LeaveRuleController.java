package com.laimengchao.sso.ehr.module.leave.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveTypeRule;
import com.laimengchao.sso.ehr.module.leave.service.LeaveTypeRuleService;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 请假类型规则：登录可读（提交弹窗按规则提示），仅 HR 可改。 */
@RestController
@RequestMapping("/api/ehr/leave-rules")
@RequiredArgsConstructor
public class LeaveRuleController {

    private final LeaveTypeRuleService ruleService;

    @GetMapping
    @Permit(PermissionCodes.LEAVE_VIEW)
    public R<List<LeaveTypeRule>> list() {
        return R.ok(ruleService.list());
    }

    @PutMapping("/{leaveType}")
    @Permit(PermissionCodes.LEAVE_RULE_MANAGE)
    public R<LeaveTypeRule> save(@PathVariable String leaveType,
                                 @RequestBody LeaveTypeRule rule) {
        rule.setLeaveType(leaveType.toUpperCase());
        return R.ok(ruleService.save(rule));
    }
}
