package com.laimengchao.sso.ehr.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.dto.QuotaAdjustRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuotaAdjust;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveQuotaAdjustMapper;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** 假期额度调整：本地建单 → 审批中心 → 终审通过后调用 LeaveQuotaService 改额度（幂等）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveQuotaAdjustService {

    private static final String BIZ_QUOTA = "ehr.leave_quota";
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";

    private final LeaveQuotaAdjustMapper adjustMapper;
    private final LeaveQuotaService quotaService;
    private final EmployeeMapper employeeMapper;
    private final ApprovalLauncher approvalLauncher;
    private final BizNoService bizNoService;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    public LeaveQuotaAdjust apply(Long quotaId, QuotaAdjustRequest req) {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow(() ->
                new BizException(ErrorCode.UNAUTHORIZED));
        LeaveQuota quota = quotaService.getById(quotaId);
        if (quota == null) {
            throw new BizException(EhrErrorCode.LEAVE_QUOTA_NOT_FOUND, "额度记录不存在");
        }
        BigDecimal delta = req.getDelta();
        if (delta.compareTo(BigDecimal.ZERO) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "调整天数不能为 0");
        }
        BigDecimal available = quotaService.available(quota);
        if (delta.compareTo(BigDecimal.ZERO) < 0 && available.add(delta).compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(EhrErrorCode.LEAVE_QUOTA_NOT_ENOUGH,
                    "调整后总额度不能低于已用 + 冻结（当前可用 " + available + " 天）");
        }
        Employee emp = employeeMapper.selectById(quota.getEmployeeId());
        LeaveQuotaAdjust adjust = new LeaveQuotaAdjust();
        adjust.setAdjustNo(bizNoService.next("ehr_leave_quota_adjust"));
        adjust.setQuotaId(quota.getId());
        adjust.setEmployeeId(quota.getEmployeeId());
        adjust.setLeaveType(quota.getLeaveType());
        adjust.setYear(quota.getYear());
        adjust.setDelta(delta);
        adjust.setReason(req.getReason().trim());
        adjust.setStatus(0);
        adjust.setOperatorId(user.getAccountId());
        adjust.setOperatorName(user.getUsername());

        approvalLauncher.inTx(() -> adjustMapper.insert(adjust));
        approvalLauncher.launch(
                () -> buildStart(adjust, quota, emp, user),
                instanceId -> { /* 实例号统一由审批中心维护 */ },
                () -> adjustMapper.update(null, new LambdaUpdateWrapper<LeaveQuotaAdjust>()
                        .eq(LeaveQuotaAdjust::getId, adjust.getId())
                        .eq(LeaveQuotaAdjust::getStatus, 0)
                        .set(LeaveQuotaAdjust::getStatus, 3)
                        .set(LeaveQuotaAdjust::getReason, "发起审批失败，系统自动撤销")));
        return adjustMapper.selectById(adjust.getId());
    }

    private ApprovalStartDTO buildStart(LeaveQuotaAdjust adjust, LeaveQuota quota,
                                        Employee emp, SecurityUser user) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_QUOTA);
        dto.setBizId(String.valueOf(adjust.getId()));
        dto.setBizNo(String.valueOf(adjust.getId()));
        dto.setTitle("假期额度调整 · " + (emp == null ? "" : emp.getName())
                + " · " + quota.getLeaveType() + " " + quota.getYear() + " 年");
        dto.setApplicantId(user.getAccountId());
        dto.setApplicantName(user.getUsername());
        dto.setApplicantOrgId(emp == null ? null : emp.getOrgId());
        dto.setOrgPath("");
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(adjust.getEmployeeId()));
        form.put("employeeName", emp == null ? "" : emp.getName());
        form.put("empNo", emp == null ? "" : emp.getEmpNo());
        form.put("leaveType", adjust.getLeaveType());
        form.put("year", adjust.getYear());
        form.put("delta", adjust.getDelta());
        form.put("beforeTotal", quota.getTotalDays());
        form.put("afterTotal", quota.getTotalDays().add(adjust.getDelta()));
        form.put("reason", adjust.getReason());
        form.put("submitter", user.getUsername());
        dto.setFormData(form);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    @Transactional(rollbackFor = Exception.class)
    public void handleResult(ApprovalCallbackRequest req) {
        Long id = parseLongOrNull(req.getBizId());
        if (id == null) {
            log.warn("额度调整回调缺少业务ID：{}", req.getBizId());
            return;
        }
        LeaveQuotaAdjust adjust = adjustMapper.selectById(id);
        if (adjust == null) {
            log.warn("额度调整回调对应的单据不存在 bizId={}", req.getBizId());
            return;
        }
        if (adjust.getStatus() == null || adjust.getStatus() != 0) {
            log.info("额度调整回调已处理过，忽略 id={} status={}", id, adjust.getStatus());
            return;
        }
        int st = Boolean.TRUE.equals(req.getApproved()) ? 1
                : (req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4)) ? 3 : 2;
        int updated = adjustMapper.update(null, new LambdaUpdateWrapper<LeaveQuotaAdjust>()
                .eq(LeaveQuotaAdjust::getId, id)
                .eq(LeaveQuotaAdjust::getStatus, 0)
                .set(LeaveQuotaAdjust::getStatus, st)
                .set(LeaveQuotaAdjust::getApprovalInstanceId, null));
        if (updated == 0) {
            return;
        }
        if (st == 1) {
            quotaService.adjust(adjust.getQuotaId(), adjust.getDelta(), adjust.getOperatorId(),
                    "额度调整审批通过：" + adjust.getReason(), "ADJUST:" + id);
        }
        log.info("额度调整审批终审已处理 id={} status={} delta={}", id, st, adjust.getDelta());
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
