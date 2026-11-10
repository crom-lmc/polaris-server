package com.laimengchao.sso.ehr.module.attendance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.attendance.dto.WorkCalendarItem;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceCalendarService;
import com.laimengchao.sso.ehr.module.attendance.vo.WorkCalendarVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 工作日历维护：month=yyyy-MM 返回整月，未配置日期按周一~周五工作日推算。 */
@RestController
@RequestMapping("/api/ehr/attendance/calendar")
@RequiredArgsConstructor
public class WorkCalendarController {

    private final AttendanceCalendarService calendarService;

    @GetMapping
    @Permit(PermissionCodes.ATTENDANCE_VIEW)
    public R<List<WorkCalendarVO>> month(@RequestParam String month) {
        return R.ok(calendarService.month(month));
    }

    @PostMapping
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<Void> save(@RequestBody List<WorkCalendarItem> items) {
        calendarService.save(items);
        return R.ok();
    }

    /** CSV 导入：日期,是否工作日,节假日名称 */
    @PostMapping("/import")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<Integer> importCsv(@RequestParam("file") MultipartFile file) {
        return R.ok(calendarService.importCsv(file));
    }

    /** 下一年工作日历状态：11 月起未更新时前端展示「更新下一年工作日历」按钮 */
    @GetMapping("/next-year-status")
    @Permit(PermissionCodes.ATTENDANCE_VIEW)
    public R<Map<String, Object>> nextYearStatus() {
        return R.ok(calendarService.nextYearStatus());
    }

    /** 一键更新下一年工作日历：写入法定节假日与调休上班日预设并关闭维护待办 */
    @PostMapping("/apply-next-year")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<Map<String, Object>> applyNextYear() {
        String operator = SecurityUtils.currentUser().map(SecurityUser::getDisplayName).orElse("");
        return R.ok(calendarService.applyNextYear(operator));
    }

}
