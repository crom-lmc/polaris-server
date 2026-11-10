package com.laimengchao.sso.ehr.module.leave.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.leave.entity.AnnualLeavePolicy;
import com.laimengchao.sso.ehr.module.leave.service.AnnualLeaveService;
import com.laimengchao.sso.ehr.module.leave.vo.AnnualLeaveLedgerVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 年假策略与年度额度生成（P4-4）。权限复用额度调整权限。 */
@RestController
@RequestMapping("/api/ehr/admin")
@RequiredArgsConstructor
public class AnnualLeaveController {

    private final AnnualLeaveService annualLeaveService;

    @GetMapping("/annual-leave-policies")
    @Permit(PermissionCodes.LEAVE_QUOTA_ADJUST)
    public R<List<AnnualLeavePolicy>> policies() {
        return R.ok(annualLeaveService.listPolicies());
    }

    @PostMapping("/annual-leave-policies")
    @Permit(PermissionCodes.LEAVE_QUOTA_ADJUST)
    public R<AnnualLeavePolicy> create(@RequestBody AnnualLeavePolicy policy) {
        policy.setId(null);
        return R.ok(annualLeaveService.savePolicy(policy));
    }

    @PutMapping("/annual-leave-policies/{id}")
    @Permit(PermissionCodes.LEAVE_QUOTA_ADJUST)
    public R<AnnualLeavePolicy> update(@PathVariable Long id,
                                       @RequestBody AnnualLeavePolicy policy) {
        policy.setId(id);
        return R.ok(annualLeaveService.savePolicy(policy));
    }

    /** 启用指定策略，同时停用其他策略 */
    @PostMapping("/annual-leave-policies/{id}/enable")
    @Permit(PermissionCodes.LEAVE_QUOTA_ADJUST)
    public R<AnnualLeavePolicy> enable(@PathVariable Long id) {
        return R.ok(annualLeaveService.enablePolicy(id));
    }

    /** 手动生成全员指定年度年假额度（联调用），返回新建数量 */
    @PostMapping("/annual-leave/generate")
    @Permit(PermissionCodes.LEAVE_QUOTA_ADJUST)
    public R<Integer> generate(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return R.ok(annualLeaveService.generateAll(y));
    }

    /** 按新规则重算指定年度全员年假额度（含已离职按在册折算），返回新建+有变化的条数 */
    @PostMapping("/annual-leave/recalculate")
    @Permit(PermissionCodes.LEAVE_QUOTA_ADJUST)
    public R<Integer> recalculate(@RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        return R.ok(annualLeaveService.recalculateYear(y, true));
    }

    /** 年假台账：每人当年产生 / 上年结转 / 已用 / 剩余 */
    @GetMapping("/annual-leave/ledger")
    @Permit(PermissionCodes.LEAVE_QUOTA_VIEW)
    public R<R.Page<AnnualLeaveLedgerVO>> ledger(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(annualLeaveService.pageLedger(keyword, year, page, size));
    }
}
