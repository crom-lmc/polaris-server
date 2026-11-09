package com.laimengchao.sso.ehr.module.attendance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.attendance.dto.OvertimeRequest;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceService;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceMonthVO;
import com.laimengchao.sso.ehr.module.attendance.vo.OvertimeVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 轻量考勤：加班申请 / 我的·团队列表 / 月度汇总与确认。 */
@RestController
@RequestMapping("/api/ehr/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/overtimes")
    @Permit(PermissionCodes.OVERTIME_VIEW)
    public R<R.Page<OvertimeVO>> list(@RequestParam(required = false) String scope,
                                      @RequestParam(required = false) Long employeeId,
                                      @RequestParam(required = false) String month,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return R.ok(attendanceService.list(scope, employeeId, month, page, size));
    }

    @PostMapping("/overtimes")
    @Permit(PermissionCodes.OVERTIME_VIEW)
    public R<OvertimeVO> submit(@RequestBody OvertimeRequest req) {
        return R.ok(attendanceService.submit(req));
    }

    @PostMapping("/overtimes/{id}/cancel")
    @Permit(PermissionCodes.OVERTIME_VIEW)
    public R<Void> cancel(@PathVariable Long id) {
        attendanceService.cancel(id);
        return R.ok();
    }

    @GetMapping("/monthly")
    @Permit(PermissionCodes.OVERTIME_VIEW)
    public R<List<AttendanceMonthVO>> monthly(@RequestParam String month,
                                              @RequestParam(required = false) Long employeeId) {
        return R.ok(attendanceService.monthly(month, employeeId));
    }

    @PostMapping("/monthly/confirm")
    @Permit(PermissionCodes.OVERTIME_APPROVE)
    public R<AttendanceMonthVO> confirm(@RequestParam String month, @RequestParam Long employeeId) {
        return R.ok(attendanceService.confirm(month, employeeId));
    }
}
