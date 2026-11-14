package com.laimengchao.sso.ehr.module.attendance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceRuleRequest;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceRuleService;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceRuleVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 考勤规则中心。 */
@RestController
@RequestMapping("/api/ehr/attendance")
@RequiredArgsConstructor
public class AttendanceRuleController {

    private final AttendanceRuleService ruleService;

    @GetMapping("/rules")
    @Permit(PermissionCodes.ATTENDANCE_VIEW)
    public R<R.Page<AttendanceRuleVO>> page(@RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return R.ok(ruleService.page(keyword, page, size));
    }

    @GetMapping("/rules/{id}")
    @Permit(PermissionCodes.ATTENDANCE_VIEW)
    public R<AttendanceRuleVO> detail(@PathVariable Long id) {
        return R.ok(ruleService.detail(id));
    }

    @PostMapping("/rules")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<AttendanceRuleVO> create(@RequestBody AttendanceRuleRequest req) {
        return R.ok(ruleService.save(null, req));
    }

    @PutMapping("/rules/{id}")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<AttendanceRuleVO> update(@PathVariable Long id, @RequestBody AttendanceRuleRequest req) {
        return R.ok(ruleService.save(id, req));
    }

    @DeleteMapping("/rules/{id}")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        ruleService.delete(id);
        return R.ok();
    }

    @PostMapping("/rules/{id}/toggle")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<AttendanceRuleVO> toggle(@PathVariable Long id) {
        return R.ok(ruleService.toggle(id));
    }
}
