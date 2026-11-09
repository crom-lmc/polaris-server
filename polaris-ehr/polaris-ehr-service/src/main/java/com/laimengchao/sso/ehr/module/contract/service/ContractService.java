package com.laimengchao.sso.ehr.module.contract.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.attachment.entity.Attachment;
import com.laimengchao.sso.ehr.module.attachment.service.AttachmentService;
import com.laimengchao.sso.ehr.module.contract.dto.ContractRenewRequest;
import com.laimengchao.sso.ehr.module.contract.dto.ContractRequest;
import com.laimengchao.sso.ehr.module.contract.dto.ContractTerminateRequest;
import com.laimengchao.sso.ehr.module.contract.entity.Contract;
import com.laimengchao.sso.ehr.module.contract.mapper.ContractMapper;
import com.laimengchao.sso.ehr.module.contract.vo.ContractVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合同与档案附件（P1-1）。
 * <p>
 * 新增/编辑直接落库；续签与终止必须走审批中心，审批通过才改状态。
 * 续签先落一条 PENDING 新合同，旧合同在回调通过时置 RENEWED；
 * 终止先把合同置 TERMINATING，回调通过置 TERMINATED、驳回恢复 ACTIVE。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    public static final String BIZ_RENEW = "ehr.contract.renew";
    public static final String BIZ_TERMINATE = "ehr.contract.terminate";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_TERMINATING = "TERMINATING";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_RENEWED = "RENEWED";
    public static final String STATUS_TERMINATED = "TERMINATED";

    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";
    private static final Map<String, String> TYPE_NAMES = Map.of(
            "FIXED", "固定期限",
            "UNFIXED", "无固定期限",
            "INTERN", "实习",
            "LABOR", "劳务"
    );
    private static final Map<String, String> STATUS_NAMES = Map.of(
            STATUS_ACTIVE, "生效中",
            STATUS_PENDING, "续签审批中",
            STATUS_TERMINATING, "终止审批中",
            STATUS_EXPIRED, "已到期",
            STATUS_RENEWED, "已续签",
            STATUS_TERMINATED, "已终止"
    );

    private final ContractMapper contractMapper;
    private final EmployeeMapper employeeMapper;
    private final DataScopeService dataScopeService;
    private final AttachmentService attachmentService;
    private final ApprovalLauncher approvalLauncher;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /* ---------- 查询 ---------- */

    public List<ContractVO> listByEmployee(Long employeeId) {
        Employee emp = requireAccessible(employeeId);
        refreshExpired();
        List<Contract> rows = contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEmployeeId, employeeId)
                .orderByDesc(Contract::getStartDate)
                .orderByDesc(Contract::getId));
        return rows.stream().map(c -> toVO(c, emp)).collect(Collectors.toList());
    }

    /* ---------- 新增 / 编辑 ---------- */

    public ContractVO create(Long employeeId, ContractRequest req) {
        Employee emp = requireAccessible(employeeId);
        validate(req.getContractType(), req.getStartDate(), req.getEndDate());
        checkOverlap(employeeId, null, req.getStartDate(), req.getEndDate());
        SecurityUser user = currentUser();
        Attachment file = req.getFileId() == null ? null
                : attachmentService.requireOwned(req.getFileId(), user.getAccountId());
        Contract c = new Contract();
        c.setEmployeeId(employeeId);
        fill(c, req);
        c.setStatus(STATUS_ACTIVE);
        contractMapper.insert(c);
        if (file != null) {
            attachmentService.bind(file.getId(), "CONTRACT", c.getId());
        }
        return toVO(c, emp);
    }

    public ContractVO update(Long contractId, ContractRequest req) {
        Contract c = requireContract(contractId);
        Employee emp = requireAccessible(c.getEmployeeId());
        if (!STATUS_ACTIVE.equals(c.getStatus()) && !STATUS_EXPIRED.equals(c.getStatus())) {
            throw new BizException(EhrErrorCode.CONTRACT_STATE_INVALID, "只有生效中或已到期的合同可以编辑");
        }
        validate(req.getContractType(), req.getStartDate(), req.getEndDate());
        checkOverlap(c.getEmployeeId(), contractId, req.getStartDate(), req.getEndDate());
        Attachment file = req.getFileId() == null ? null
                : attachmentService.requireOwned(req.getFileId(), currentUser().getAccountId());
        fill(c, req);
        contractMapper.updateById(c);
        if (file != null) {
            attachmentService.bind(file.getId(), "CONTRACT", c.getId());
        }
        return toVO(c, emp);
    }

    /* ---------- 续签 / 终止（审批中心） ---------- */

    public ContractVO renew(Long contractId, ContractRenewRequest req) {
        Contract old = requireContract(contractId);
        Employee emp = requireAccessible(old.getEmployeeId());
        if (!STATUS_ACTIVE.equals(old.getStatus()) && !STATUS_EXPIRED.equals(old.getStatus())) {
            throw new BizException(EhrErrorCode.CONTRACT_STATE_INVALID, "只有生效中或已到期的合同可以续签");
        }
        if (req.getStartDate() == null) {
            throw new BizException(EhrErrorCode.CONTRACT_INVALID, "请填写新合同开始日期");
        }
        validate(old.getContractType(), req.getStartDate(), req.getEndDate());
        if (req.getEndDate() != null && old.getEndDate() != null
                && req.getStartDate().isBefore(old.getEndDate())) {
            throw new BizException(EhrErrorCode.CONTRACT_INVALID, "新合同开始日期不能早于原合同结束日期");
        }
        if (contractMapper.selectCount(new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEmployeeId, old.getEmployeeId())
                .eq(Contract::getRenewFromId, old.getId())
                .eq(Contract::getStatus, STATUS_PENDING)) > 0) {
            throw new BizException(EhrErrorCode.CONTRACT_CONFLICT, "该合同已有续签审批在进行中");
        }
        SecurityUser user = currentUser();
        Attachment file = req.getFileId() == null ? null
                : attachmentService.requireOwned(req.getFileId(), user.getAccountId());

        Contract pending = new Contract();
        pending.setEmployeeId(old.getEmployeeId());
        pending.setContractNo(req.getContractNo() == null ? old.getContractNo() : req.getContractNo());
        pending.setContractType(old.getContractType());
        pending.setStartDate(req.getStartDate());
        pending.setEndDate(req.getEndDate());
        pending.setSignDate(req.getSignDate());
        pending.setRemark(req.getRemark());
        pending.setRenewFromId(old.getId());
        pending.setStatus(STATUS_PENDING);

        approvalLauncher.inTx(() -> {
            contractMapper.insert(pending);
            if (file != null) {
                attachmentService.bind(file.getId(), "CONTRACT", pending.getId());
            }
        });
        approvalLauncher.launch(
                () -> buildRenewStart(old, pending, emp, req.getReason(), user),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                () -> {
                    contractMapper.deleteById(pending.getId());
                    if (file != null) {
                        attachmentService.bind(file.getId(), "TEMP", 0L);
                    }
                });
        Contract fresh = contractMapper.selectById(pending.getId());
        return toVO(fresh == null ? old : fresh, emp);
    }

    public ContractVO terminate(Long contractId, ContractTerminateRequest req) {
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写终止原因");
        }
        Contract c = requireContract(contractId);
        Employee emp = requireAccessible(c.getEmployeeId());
        if (!STATUS_ACTIVE.equals(c.getStatus())) {
            throw new BizException(EhrErrorCode.CONTRACT_STATE_INVALID, "只有生效中的合同可以终止");
        }
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, contractId)
                .eq(Contract::getStatus, STATUS_ACTIVE)
                .set(Contract::getStatus, STATUS_TERMINATING));
        if (updated == 0) {
            throw new BizException(EhrErrorCode.CONTRACT_CONFLICT, "合同状态已变化，请刷新后重试");
        }
        SecurityUser user = currentUser();
        approvalLauncher.launch(
                () -> buildTerminateStart(c, emp, req.getReason().trim(), user),
                instanceId -> { /* 实例号已退役 */ },
                () -> contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                        .eq(Contract::getId, contractId)
                        .eq(Contract::getStatus, STATUS_TERMINATING)
                        .set(Contract::getStatus, STATUS_ACTIVE)));
        Contract fresh = contractMapper.selectById(contractId);
        return toVO(fresh == null ? c : fresh, emp);
    }

    /* ---------- 审批回调 ---------- */

    @Transactional(rollbackFor = Exception.class)
    public void handleApprovalResult(ApprovalCallbackRequest req) {
        if (BIZ_RENEW.equals(req.getBizType())) {
            handleRenewResult(req);
        } else if (BIZ_TERMINATE.equals(req.getBizType())) {
            handleTerminateResult(req);
        }
    }

    private void handleRenewResult(ApprovalCallbackRequest req) {
        Long id = parseLong(req.getBizId());
        if (id == null) {
            return;
        }
        Contract pending = contractMapper.selectById(id);
        if (pending == null || !STATUS_PENDING.equals(pending.getStatus())) {
            log.info("续签回调重复投递或单据不存在，忽略 contractId={}", id);
            return;
        }
        if (Boolean.TRUE.equals(req.getApproved())) {
            int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                    .eq(Contract::getId, id)
                    .eq(Contract::getStatus, STATUS_PENDING)
                    .set(Contract::getStatus, STATUS_ACTIVE));
            if (updated > 0 && pending.getRenewFromId() != null) {
                contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                        .eq(Contract::getId, pending.getRenewFromId())
                        .in(Contract::getStatus, STATUS_ACTIVE, STATUS_EXPIRED)
                        .set(Contract::getStatus, STATUS_RENEWED));
            }
        } else {
            contractMapper.deleteById(id);
        }
    }

    private void handleTerminateResult(ApprovalCallbackRequest req) {
        Long id = parseLong(req.getBizId());
        if (id == null) {
            return;
        }
        String target = Boolean.TRUE.equals(req.getApproved()) ? STATUS_TERMINATED : STATUS_ACTIVE;
        int updated = contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getId, id)
                .eq(Contract::getStatus, STATUS_TERMINATING)
                .set(Contract::getStatus, target));
        if (updated == 0) {
            log.info("终止回调重复投递或状态不匹配，忽略 contractId={}", id);
        }
    }

    /* ---------- 到期处理 ---------- */

    /** 把已过结束日期的生效合同置为 EXPIRED；每日提醒任务与合同列表都会调用 */
    public int refreshExpired() {
        return contractMapper.update(null, new LambdaUpdateWrapper<Contract>()
                .eq(Contract::getStatus, STATUS_ACTIVE)
                .isNotNull(Contract::getEndDate)
                .lt(Contract::getEndDate, LocalDate.now())
                .set(Contract::getStatus, STATUS_EXPIRED));
    }

    public String typeName(String type) {
        return TYPE_NAMES.getOrDefault(type, type);
    }

    public String statusName(String status) {
        return STATUS_NAMES.getOrDefault(status, status);
    }

    /* ---------- 内部工具 ---------- */

    private ApprovalStartDTO buildRenewStart(Contract old, Contract pending, Employee emp,
                                             String reason, SecurityUser user) {
        ApprovalStartDTO dto = commonStart(emp, user, pending.getId());
        dto.setBizType(BIZ_RENEW);
        dto.setTitle("合同续签审批 · " + nvl(emp.getName()));
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(emp.getId()));
        form.put("empNo", nvl(emp.getEmpNo()));
        form.put("employeeName", nvl(emp.getName()));
        form.put("contractType", old.getContractType());
        form.put("contractTypeName", typeName(old.getContractType()));
        form.put("oldStartDate", old.getStartDate() == null ? null : old.getStartDate().toString());
        form.put("oldEndDate", old.getEndDate() == null ? null : old.getEndDate().toString());
        form.put("startDate", pending.getStartDate() == null ? null : pending.getStartDate().toString());
        form.put("endDate", pending.getEndDate() == null ? null : pending.getEndDate().toString());
        form.put("reason", nvl(reason));
        dto.setFormData(form);
        return dto;
    }

    private ApprovalStartDTO buildTerminateStart(Contract c, Employee emp,
                                                 String reason, SecurityUser user) {
        ApprovalStartDTO dto = commonStart(emp, user, c.getId());
        dto.setBizType(BIZ_TERMINATE);
        dto.setTitle("合同终止审批 · " + nvl(emp.getName()));
        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(emp.getId()));
        form.put("empNo", nvl(emp.getEmpNo()));
        form.put("employeeName", nvl(emp.getName()));
        form.put("contractNo", nvl(c.getContractNo()));
        form.put("contractTypeName", typeName(c.getContractType()));
        form.put("startDate", c.getStartDate() == null ? null : c.getStartDate().toString());
        form.put("endDate", c.getEndDate() == null ? null : c.getEndDate().toString());
        form.put("reason", reason);
        dto.setFormData(form);
        return dto;
    }

    private ApprovalStartDTO commonStart(Employee emp, SecurityUser user, Long bizId) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizId(String.valueOf(bizId));
        dto.setBizNo(String.valueOf(bizId));
        dto.setSubjectId(String.valueOf(emp.getId()));
        dto.setApplicantId(user.getAccountId());
        dto.setApplicantName(user.getDisplayName());
        dto.setApplicantOrgId(emp.getOrgId());
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    private Employee requireAccessible(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工不存在");
        }
        if (!dataScopeService.canAccess(emp)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工的合同");
        }
        return emp;
    }

    private Contract requireContract(Long contractId) {
        Contract c = contractId == null ? null : contractMapper.selectById(contractId);
        if (c == null) {
            throw new BizException(EhrErrorCode.CONTRACT_NOT_FOUND, "合同不存在");
        }
        return c;
    }

    private void validate(String type, LocalDate start, LocalDate end) {
        if (!TYPE_NAMES.containsKey(type)) {
            throw new BizException(EhrErrorCode.CONTRACT_INVALID, "合同类型不合法");
        }
        if (start == null) {
            throw new BizException(EhrErrorCode.CONTRACT_INVALID, "请填写合同开始日期");
        }
        if (!"UNFIXED".equals(type) && end == null) {
            throw new BizException(EhrErrorCode.CONTRACT_INVALID, "非无固定期限合同必须填写结束日期");
        }
        if (end != null && end.isBefore(start)) {
            throw new BizException(EhrErrorCode.CONTRACT_INVALID, "合同结束日期不能早于开始日期");
        }
    }

    private void checkOverlap(Long employeeId, Long excludeId, LocalDate start, LocalDate end) {
        LambdaQueryWrapper<Contract> w = new LambdaQueryWrapper<Contract>()
                .eq(Contract::getEmployeeId, employeeId)
                .in(Contract::getStatus, STATUS_ACTIVE, STATUS_PENDING, STATUS_TERMINATING, STATUS_EXPIRED)
                .ne(excludeId != null, Contract::getId, excludeId);
        if (end != null) {
            w.lt(Contract::getStartDate, end)
                    .and(q -> q.isNull(Contract::getEndDate).or().ge(Contract::getEndDate, start));
        } else {
            w.and(q -> q.isNull(Contract::getEndDate).or().ge(Contract::getEndDate, start));
        }
        if (contractMapper.selectCount(w) > 0) {
            throw new BizException(EhrErrorCode.CONTRACT_CONFLICT, "合同日期与已有合同重叠");
        }
    }

    private void fill(Contract c, ContractRequest req) {
        c.setContractNo(nvl(req.getContractNo()));
        c.setContractType(req.getContractType());
        c.setStartDate(req.getStartDate());
        c.setEndDate(req.getEndDate());
        c.setSignDate(req.getSignDate());
        c.setFileId(req.getFileId());
        c.setRemark(nvl(req.getRemark()));
    }

    private ContractVO toVO(Contract c, Employee emp) {
        ContractVO vo = new ContractVO();
        vo.setId(c.getId());
        vo.setEmployeeId(c.getEmployeeId());
        vo.setEmployeeName(emp == null ? "" : nvl(emp.getName()));
        vo.setContractNo(nvl(c.getContractNo()));
        vo.setContractType(c.getContractType());
        vo.setContractTypeName(typeName(c.getContractType()));
        vo.setStartDate(c.getStartDate());
        vo.setEndDate(c.getEndDate());
        vo.setSignDate(c.getSignDate());
        vo.setStatus(c.getStatus());
        vo.setStatusName(statusName(c.getStatus()));
        vo.setRenewFromId(c.getRenewFromId());
        vo.setFileId(c.getFileId());
        vo.setFileName(attachmentService.nameOf(c.getFileId()));
        vo.setRemark(nvl(c.getRemark()));
        vo.setCreatedAt(c.getCreatedAt());
        if (c.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), c.getEndDate());
            vo.setExpireDays(days);
            if ((STATUS_ACTIVE.equals(c.getStatus()) || STATUS_EXPIRED.equals(c.getStatus())) && days <= 7) {
                vo.setExpireFlag("RED");
            } else if (STATUS_ACTIVE.equals(c.getStatus()) && days <= 30) {
                vo.setExpireFlag("YELLOW");
            }
        }
        return vo;
    }

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow();
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
