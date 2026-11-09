package com.laimengchao.sso.ehr.module.employee.controller;

import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeChangeService;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心终审结果的接收端。
 * <p>
 * 由 settings 在审批结束、事务提交后回传（见 ApprovalCallbackService）。
 * 走 {@code /internal/**}，凭 X-Internal-Token 准入，不经网关。
 * <p>
 * <b>为什么成功/失败要区分 HTTP 状态码</b>：settings 的投递器只认 2xx。
 * 如果这里按常规返回 200 + code=5000，它会以为送达成功，从此不再重试，
 * 审批过了而员工状态没跟着变。所以真出异常必须给 5xx，让它退避重试。
 * <p>
 * 反过来，业务上「已处理过」「不是当前单」「员工已删除」这类情况属于<b>正常完成</b>，
 * 要给 200：重复投递本来就是幂等逻辑要吃掉的东西，不该触发重试。
 */
@Slf4j
@RestController
@RequestMapping("/internal/ehr")
@RequiredArgsConstructor
public class InternalApprovalCallbackController {

    /** 与 settings 的 cfg_biz_scene.biz_type 对齐 */
    private static final String BIZ_ONBOARD = "ehr.onboard";
    /** 部门设置负责人（旧编码 ehr.org_leader 保留兼容，历史单据回调仍可能带它） */
    private static final String BIZ_DEPT_LEADER = "ehr.dept_leader";
    private static final String BIZ_ORG_LEADER_LEGACY = "ehr.org_leader";
    /** 人员信息变更（试用期/在职员工改个人信息） */
    private static final String BIZ_EMP_UPDATE = "ehr.emp_update";
    /** 转正 / 请假 / 离职 / 减员 / 复职：全部走审批中心 */
    private static final String BIZ_REGULARIZE = "ehr.regularize";
    private static final String BIZ_LEAVE = "ehr.leave";
    private static final String BIZ_RESIGN = "ehr.resign";
    private static final String BIZ_REDUCE = "ehr.reduce";
    private static final String BIZ_REHIRE = "ehr.rehire";
    /** 假期额度调整审批 */
    private static final String BIZ_LEAVE_QUOTA = "ehr.leave_quota";
    /** 合同续签 / 终止审批 */
    private static final String BIZ_CONTRACT_RENEW = "ehr.contract.renew";
    private static final String BIZ_CONTRACT_TERMINATE = "ehr.contract.terminate";
    /** 加班审批 */
    private static final String BIZ_OVERTIME = "ehr.overtime";
    private static final String BIZ_OVERTIME_HR = "ehr.overtime.hr";

    private final EmployeeService employeeService;
    private final EmployeeChangeService employeeChangeService;
    private final com.laimengchao.sso.ehr.module.org.service.OrgService orgService;
    private final com.laimengchao.sso.ehr.module.leave.service.LeaveService leaveService;
    private final com.laimengchao.sso.ehr.module.leave.service.LeaveQuotaAdjustService leaveQuotaAdjustService;
    private final com.laimengchao.sso.ehr.module.approval.service.ResignApplyService resignApplyService;
    private final com.laimengchao.sso.ehr.module.approval.service.RehireApplyService rehireApplyService;
    private final com.laimengchao.sso.ehr.module.approval.service.RegularizeApplyService regularizeApplyService;
    private final com.laimengchao.sso.ehr.module.contract.service.ContractService contractService;
    private final com.laimengchao.sso.ehr.module.attendance.service.AttendanceService attendanceService;

    @PostMapping("/approval-callback")
    public ResponseEntity<R<Void>> approvalCallback(@RequestBody ApprovalCallbackRequest req) {
        log.info("收到审批终审回调 instance={} bizType={} bizId={} result={}",
                req.getInstanceId(), req.getBizType(), req.getBizId(), req.getResult());
        try {
            if (BIZ_ONBOARD.equals(req.getBizType())) {
                employeeService.handleOnboardApprovalResult(req);
            } else if (BIZ_DEPT_LEADER.equals(req.getBizType())
                    || BIZ_ORG_LEADER_LEGACY.equals(req.getBizType())) {
                orgService.handleLeaderApprovalResult(req);
            } else if (BIZ_EMP_UPDATE.equals(req.getBizType())) {
                employeeChangeService.handleApprovalResult(req);
            } else if (BIZ_REGULARIZE.equals(req.getBizType())) {
                regularizeApplyService.handleRegularizeApprovalResult(req);
            } else if (BIZ_LEAVE.equals(req.getBizType())) {
                leaveService.handleLeaveApprovalResult(req);
            } else if (BIZ_LEAVE_QUOTA.equals(req.getBizType())) {
                leaveQuotaAdjustService.handleResult(req);
            } else if (BIZ_RESIGN.equals(req.getBizType()) || BIZ_REDUCE.equals(req.getBizType())) {
                // 离职（员工自助）与减员（HR）是两条流程，但终态动作一样：置离职 + 停用账号
                resignApplyService.handleResignApprovalResult(req);
            } else if (BIZ_REHIRE.equals(req.getBizType())) {
                rehireApplyService.handleRehireApprovalResult(req);
            } else if (BIZ_CONTRACT_RENEW.equals(req.getBizType())
                    || BIZ_CONTRACT_TERMINATE.equals(req.getBizType())) {
                contractService.handleApprovalResult(req);
            } else if (BIZ_OVERTIME.equals(req.getBizType()) || BIZ_OVERTIME_HR.equals(req.getBizType())) {
                attendanceService.handleApprovalResult(req);
            } else {
                // 未知场景收下不处理：直接回 5xx 会让 settings 无意义地重试 8 次
                log.info("审批回调的业务场景尚未接入，忽略：bizType={}", req.getBizType());
            }
            return ResponseEntity.ok(R.ok());
        } catch (Exception e) {
            log.error("处理审批回调失败 instance={} bizType={} bizId={}",
                    req.getInstanceId(), req.getBizType(), req.getBizId(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(R.fail(ErrorCode.SYSTEM_ERROR, "审批结果处理失败，请稍后重试"));
        }
    }
}
