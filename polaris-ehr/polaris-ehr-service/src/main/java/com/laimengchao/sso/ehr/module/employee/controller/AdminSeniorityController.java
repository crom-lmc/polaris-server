package com.laimengchao.sso.ehr.module.employee.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.employee.dto.SeniorityConfirmRequest;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeSeniorityRecord;
import com.laimengchao.sso.ehr.module.employee.entity.EmployeeSenioritySegment;
import com.laimengchao.sso.ehr.module.employee.service.SeniorityService;
import com.laimengchao.sso.ehr.module.employee.vo.SeniorityVO;
import com.laimengchao.sso.ehr.security.Permit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 司龄管理与核定（P4-4）：列表可看员工司龄，HR 核定后回写档案并作为年假档位依据。 */
@RestController
@RequestMapping("/api/ehr/admin/seniority")
@RequiredArgsConstructor
public class AdminSeniorityController {

    private final SeniorityService seniorityService;

    @GetMapping
    @Permit(PermissionCodes.EMPLOYEE_VIEW)
    public R<R.Page<SeniorityVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(seniorityService.page(keyword, employeeId, page, size));
    }

    /** 某员工的司龄核定历史（生效日倒序） */
    @GetMapping("/{employeeId}/records")
    @Permit(PermissionCodes.EMPLOYEE_VIEW)
    public R<List<EmployeeSeniorityRecord>> records(@PathVariable Long employeeId) {
        return R.ok(seniorityService.records(employeeId));
    }

    /** 某员工最近一次核定的「前司工作段」（多段不连续工作经历），用于核定弹窗回显 */
    @GetMapping("/{employeeId}/segments")
    @Permit(PermissionCodes.EMPLOYEE_VIEW)
    public R<List<EmployeeSenioritySegment>> segments(@PathVariable Long employeeId) {
        return R.ok(seniorityService.latestSegments(employeeId));
    }

    /** 核定司龄：追加核定记录并回写员工档案 */
    @PostMapping("/{employeeId}")
    @Permit(PermissionCodes.EMPLOYEE_EDIT)
    public R<SeniorityVO> confirm(@PathVariable Long employeeId,
                                  @Valid @RequestBody SeniorityConfirmRequest req) {
        return R.ok(seniorityService.confirm(employeeId, req));
    }
}
