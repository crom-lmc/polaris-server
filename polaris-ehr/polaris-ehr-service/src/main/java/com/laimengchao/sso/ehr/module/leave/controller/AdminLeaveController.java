package com.laimengchao.sso.ehr.module.leave.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.leave.dto.EarlyReturnRequest;
import com.laimengchao.sso.ehr.module.leave.dto.LeaveApplyRequest;
import com.laimengchao.sso.ehr.module.leave.service.LeaveService;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveDelegateOptionsVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeavePolicyVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeavePreviewVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveVO;
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
import java.util.Map;

/**
 * 请假：申请与审批分离成两个权限码（leave:apply / leave:approve），
 * 默认角色里基本功能只给申请，请假审批角色才给 approve。
 */
@RestController
@RequestMapping("/api/ehr/leaves")
@RequiredArgsConstructor
public class AdminLeaveController {

    private final LeaveService leaveService;

    /** scope=mine 我的记录；scope=all 全部记录（需 leave:approve） */
    @GetMapping
    @Permit(PermissionCodes.LEAVE_VIEW)
    public R<R.Page<LeaveVO>> page(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(leaveService.page(scope, status, page, size));
    }

    @PostMapping
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<LeaveVO> apply(@Valid @RequestBody LeaveApplyRequest req) {
        return R.ok(leaveService.apply(req));
    }

    @PostMapping("/{id}/cancel")
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<LeaveVO> cancel(@PathVariable Long id) {
        return R.ok(leaveService.cancel(id));
    }

    /** 提前返岗销假：按实际返岗日期重算天数并退回额度 */
    @PostMapping("/{id}/early-return")
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<LeaveVO> earlyReturn(@PathVariable Long id,
                                  @Valid @RequestBody EarlyReturnRequest req) {
        return R.ok(leaveService.earlyReturn(id, req));
    }

    /** 提交前的时段冲突提示（只查本人） */
    @GetMapping("/conflicts")
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<List<LeaveVO>> conflicts(@RequestParam String startDate,
                                      @RequestParam String endDate) {
        return R.ok(leaveService.conflicts(startDate, endDate));
    }

    /** 团队请假视图：按组织子树 + 日期区间 */
    @GetMapping("/team")
    @Permit(PermissionCodes.LEAVE_VIEW_TEAM)
    public R<List<LeaveVO>> team(@RequestParam(required = false) Long orgId,
                                 @RequestParam(required = false) String from,
                                 @RequestParam(required = false) String to) {
        return R.ok(leaveService.team(orgId, from, to));
    }

    /** 请假类型下拉（配置中心 biz_leave_type 主数据；登录即可，提交请假的人都要用） */
    @GetMapping("/type-options")
    public R<List<TypeOption>> typeOptions() {
        return R.ok(leaveService.typeNames().entrySet().stream()
                .map(e -> new TypeOption(e.getKey(), e.getValue()))
                .toList());
    }

    /** 审批转办选项：部门负责人请假时该字段必填，候选人=在职且已开户的其他员工 */
    @GetMapping("/delegate-options")
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<LeaveDelegateOptionsVO> delegateOptions() {
        return R.ok(leaveService.delegateOptions());
    }

    /** 当前员工所在城市的婚假 / 产假 / 陪产假天数与计算方式 */
    @GetMapping("/city-policy")
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<LeavePolicyVO> cityPolicy() {
        return R.ok(leaveService.cityPolicy());
    }

    /** 按开始日期试算结束日期（城市政策类假期天数固定） */
    @GetMapping("/plan")
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<LeavePolicyVO> plan(@RequestParam String leaveType,
                                 @RequestParam String startDate) {
        return R.ok(leaveService.plan(leaveType, startDate));
    }

    /** 申请弹窗「本次请假时长」试算：与提交共用同一套天数计算口径 */
    @GetMapping("/preview")
    @Permit(PermissionCodes.LEAVE_APPLY)
    public R<LeavePreviewVO> preview(@RequestParam String leaveType,
                                     @RequestParam String startDate,
                                     @RequestParam String endDate,
                                     @RequestParam(required = false) String startTime,
                                     @RequestParam(required = false) String endTime,
                                     @RequestParam(required = false) String dayMode) {
        return R.ok(leaveService.preview(leaveType, startDate, endDate, startTime, endTime, dayMode));
    }

    public record TypeOption(String value, String label) {
    }
}
