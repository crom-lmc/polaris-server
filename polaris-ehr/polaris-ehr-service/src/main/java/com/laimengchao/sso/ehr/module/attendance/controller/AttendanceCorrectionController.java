package com.laimengchao.sso.ehr.module.attendance.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceCorrectionApproveRequest;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendanceCorrectionRequest;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceCorrectionService;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceCorrectionVO;
import com.laimengchao.sso.ehr.security.Permit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 补卡申请与 HR 直审。 */
@RestController
@RequestMapping("/api/ehr/attendance")
@RequiredArgsConstructor
public class AttendanceCorrectionController {

    private final AttendanceCorrectionService correctionService;

    @PostMapping("/corrections")
    public R<AttendanceCorrectionVO> apply(@RequestBody AttendanceCorrectionRequest req) {
        return R.ok(correctionService.apply(req));
    }

    /** HR 看全部，员工只看本人。 */
    @GetMapping("/corrections")
    public R<R.Page<AttendanceCorrectionVO>> page(@RequestParam(required = false) Integer status,
                                                  @RequestParam(required = false) String month,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return R.ok(correctionService.page(status, month, page, size));
    }

    @PostMapping("/corrections/{id}/approve")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<AttendanceCorrectionVO> approve(@PathVariable Long id,
                                             @RequestBody(required = false) AttendanceCorrectionApproveRequest req) {
        return R.ok(correctionService.approve(id, req));
    }

    @PostMapping("/corrections/{id}/reject")
    @Permit(PermissionCodes.ATTENDANCE_MANAGE)
    public R<AttendanceCorrectionVO> reject(@PathVariable Long id,
                                            @RequestBody(required = false) AttendanceCorrectionApproveRequest req) {
        return R.ok(correctionService.reject(id, req));
    }

    @PostMapping("/corrections/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        correctionService.cancel(id);
        return R.ok();
    }
}
