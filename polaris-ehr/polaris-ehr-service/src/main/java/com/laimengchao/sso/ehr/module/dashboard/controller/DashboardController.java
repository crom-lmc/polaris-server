package com.laimengchao.sso.ehr.module.dashboard.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.dashboard.service.DashboardSummaryService;
import com.laimengchao.sso.ehr.module.dashboard.vo.DashboardSummaryVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 人力看板：一次返回概览、部门分布、编制 vs 实有、请假趋势。
 * <p>
 * 所有指标都由 {@link DashboardSummaryService} 按当前账号的数据范围计算，
 * 普通员工只会看到自己范围内的数字，因此不需要额外的运营权限码。
 */
@RestController
@RequestMapping("/api/ehr/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardSummaryService summaryService;

    @GetMapping("/summary")
    @Permit(PermissionCodes.EMPLOYEE_VIEW)
    public R<DashboardSummaryVO> summary(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return R.ok(summaryService.summary(orgId, from, to));
    }
}
