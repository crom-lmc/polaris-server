package com.laimengchao.sso.ehr.module.leave.controller;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.dto.QuotaAdjustRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuotaAdjust;
import com.laimengchao.sso.ehr.module.leave.service.LeaveQuotaAdjustService;
import com.laimengchao.sso.ehr.module.leave.service.LeaveQuotaService;
import com.laimengchao.sso.ehr.module.leave.service.LeaveService;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveQuotaVO;
import com.laimengchao.sso.ehr.module.leave.vo.QuotaLogVO;
import com.laimengchao.sso.ehr.security.Permit;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 假期额度查询、初始化与调整（调整走审批中心，通过后才生效）。 */
@RestController
@RequestMapping("/api/ehr/leave-quotas")
@RequiredArgsConstructor
public class LeaveQuotaController {

    private final LeaveQuotaService quotaService;
    private final LeaveQuotaAdjustService adjustService;
    private final LeaveService leaveService;
    private final EmployeeMapper employeeMapper;

    /** 我的额度：登录即可，前端请假弹窗展示余额 */
    @GetMapping("/me")
    public R<List<LeaveQuotaVO>> mine(@RequestParam(required = false) Integer year) {
        Long employeeId = leaveService.myEmployeeId();
        return R.ok(fillNames(quotaService.listForEmployee(employeeId, year == null ? LocalDate.now().getYear() : year)));
    }

    /** 指定员工的额度：HR / 主管 */
    @GetMapping
    @Permit(PermissionCodes.LEAVE_QUOTA_VIEW)
    public R<List<LeaveQuotaVO>> byEmployee(@RequestParam Long employeeId,
                                            @RequestParam(required = false) Integer year) {
        return R.ok(fillNames(quotaService.listForEmployee(employeeId,
                year == null ? LocalDate.now().getYear() : year)));
    }

    /** 初始化某员工年度额度（按类型规则默认额度） */
    @PostMapping("/init")
    @Permit(PermissionCodes.LEAVE_QUOTA_MANAGE)
    public R<List<LeaveQuotaVO>> init(@RequestParam Long employeeId,
                                      @RequestParam(required = false) Integer year) {
        int y = year == null ? LocalDate.now().getYear() : year;
        quotaService.initForEmployee(employeeId, y);
        return R.ok(fillNames(quotaService.listForEmployee(employeeId, y)));
    }

    /** 额度流水 */
    @GetMapping("/{quotaId}/logs")
    @Permit(PermissionCodes.LEAVE_QUOTA_VIEW)
    public R<List<QuotaLogVO>> logs(@PathVariable Long quotaId) {
        return R.ok(quotaService.logs(quotaId));
    }

    /** 发起额度调整审批 */
    @PostMapping("/{quotaId}/adjust")
    @Permit(PermissionCodes.LEAVE_QUOTA_ADJUST)
    public R<LeaveQuotaAdjust> adjust(@PathVariable Long quotaId,
                                      @Valid @RequestBody QuotaAdjustRequest req) {
        return R.ok(adjustService.apply(quotaId, req));
    }

    private List<LeaveQuotaVO> fillNames(List<LeaveQuotaVO> list) {
        Map<String, String> names = leaveService.typeNames();
        for (LeaveQuotaVO vo : list) {
            vo.setLeaveTypeName(names.getOrDefault(vo.getLeaveType(), vo.getLeaveType()));
        }
        return list;
    }
}
