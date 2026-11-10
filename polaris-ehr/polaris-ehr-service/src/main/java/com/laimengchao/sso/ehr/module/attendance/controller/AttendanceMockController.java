package com.laimengchao.sso.ehr.module.attendance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceMockService;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 手动触发模拟考勤（受 ehr.attendance.mock.enabled 开关限制，关闭时返回明确提示）。 */
@RestController
@RequestMapping("/api/ehr/attendance")
@RequiredArgsConstructor
public class AttendanceMockController {

    private final AttendanceMockService mockService;

    @PostMapping("/mock/run")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<Integer> run(@RequestParam(required = false)
                          @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return R.ok(mockService.run(date));
    }
}
