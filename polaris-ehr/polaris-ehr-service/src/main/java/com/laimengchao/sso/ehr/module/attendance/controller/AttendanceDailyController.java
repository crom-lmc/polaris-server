package com.laimengchao.sso.ehr.module.attendance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendancePunchRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendancePunch;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceDailyService;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceDailyVO;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendancePunchVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** 考勤日结果与打卡记录。 */
@RestController
@RequestMapping("/api/ehr/attendance")
@RequiredArgsConstructor
public class AttendanceDailyController {

    private final AttendanceDailyService dailyService;

    @GetMapping("/daily")
    @Permit(PermissionCodes.ATTENDANCE_VIEW)
    public R<R.Page<AttendanceDailyVO>> page(@RequestParam String month,
                                             @RequestParam(required = false) Long employeeId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return R.ok(dailyService.page(month, employeeId, status, page, size));
    }

    @PostMapping("/daily/generate")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<Integer> generate(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(dailyService.generate(date));
    }

    /** 当前登录员工本人某月考勤（无需 HR 权限）。 */
    @GetMapping("/my")
    public R<List<AttendanceDailyVO>> my(@RequestParam String month) {
        return R.ok(dailyService.my(month));
    }

    @GetMapping("/punches")
    @Permit(PermissionCodes.ATTENDANCE_VIEW)
    public R<R.Page<AttendancePunchVO>> punches(@RequestParam(required = false) Long employeeId,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return R.ok(dailyService.punches(employeeId, date, page, size));
    }

    @PostMapping("/punches")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<AttendancePunch> addPunch(@RequestBody AttendancePunchRequest req) {
        return R.ok(dailyService.addPunch(req));
    }
}
