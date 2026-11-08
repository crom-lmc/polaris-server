package com.laimengchao.sso.ehr.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.approval.dto.RehireApplyRequest;
import com.laimengchao.sso.ehr.module.approval.entity.RehireApply;
import com.laimengchao.sso.ehr.module.approval.mapper.RehireApplyMapper;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.approval.vo.RehireApplyVO;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.employee.service.EmployeeService;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.security.SecurityUser;
import com.laimengchao.sso.ehr.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 复职申请服务（离职 / 减员后回聘）。
 * <p>
 * <b>审批统一走 settings 审批中心</b>：{@code ehr.rehire} 模板决定几步、谁审。
 * 本服务只做三件事——提交（写申请单 + 发起实例）、收终审回调、撤销。
 * <p>
 * 三段式发起由 {@link ApprovalLauncher} 托管：本地申请单先独立提交，再远程 start，
 * 只有实例仍在途才回写实例号。原因见 ApprovalLauncher 的类注释（同步回调竞态）。
 * <p>
 * 与旧接口 {@code /employees/{id}/reinstate} 的区别：那个是直办（历史数据纠错用），
 * 这个是正常流程，审批通过才真的把人放回在职并启用账号。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RehireApplyService {

    /** 复职审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    public static final String BIZ_REHIRE = "ehr.rehire";
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "审批中",
            1, "已通过",
            2, "已驳回",
            3, "已撤销");

    private final RehireApplyMapper mapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final EmployeeService employeeService;
    private final ApprovalApi approvalApi;
    private final ApprovalLauncher approvalLauncher;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /* ---------------- 提交 ---------------- */

    /**
     * 提交复职申请。刻意不加 {@code @Transactional}：
     * 本地写入由 {@code approvalLauncher.inTx} 独立提交，远程发起必须在其之外。
     */
    public RehireApplyVO submit(Long employeeId, RehireApplyRequest req) {
        SecurityUser user = currentUser();
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND);
        }
        if (emp.getStatus() == null || emp.getStatus() != EmployeeStatus.LEFT.getCode()) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID,
                    "只有已离职的员工可以办理复职；在职人员无需复职");
        }
        if (approvalLauncher.hasRunning(String.valueOf(emp.getId()), null)) {
            throw new BizException(EhrErrorCode.APPROVAL_DUPLICATED, "该员工已有在审批中的单据，请先处理完再提交复职");
        }
        Long pending = mapper.selectCount(new LambdaQueryWrapper<RehireApply>()
                .eq(RehireApply::getEmployeeId, employeeId)
                .eq(RehireApply::getStatus, 0));
        if (pending != null && pending > 0) {
            throw new BizException(EhrErrorCode.APPROVAL_DUPLICATED,
                    "该员工已有一张审批中的复职申请，请先处理完再提交");
        }
        LocalDate rehireDate = parseDate(req == null ? null : req.getRehireDate());
        if (rehireDate == null) {
            rehireDate = LocalDate.now();
        }
        if (emp.getResignDate() != null && rehireDate.isBefore(emp.getResignDate())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "复职日期不能早于离职日期");
        }

        OrgNode org = emp.getOrgId() == null ? null : orgMapper.selectById(emp.getOrgId());
        RehireApply apply = new RehireApply();
        apply.setEmployeeId(emp.getId());
        apply.setEmpNo(emp.getEmpNo());
        apply.setEmployeeName(emp.getName());
        apply.setOrgId(emp.getOrgId());
        apply.setOrgName(org == null ? "" : nvl(org.getName()));
        apply.setResignDate(emp.getResignDate());
        apply.setRehireDate(rehireDate);
        apply.setReason(req == null || req.getReason() == null ? "" : req.getReason().trim());
        apply.setEnableAccount(req == null || req.getEnableAccount() == null
                || Boolean.TRUE.equals(req.getEnableAccount()) ? 1 : 0);
        apply.setStatus(0);
        apply.setApplicantAccountId(user.getAccountId());
        apply.setApplicantName(applicantName(user));

        // ① 本地写入先独立提交：settings 的同步回调发生时这张单一定可见
        approvalLauncher.inTx(() -> mapper.insert(apply));

        // ②③ 远程发起 + 仅在途才回写（员工档案同步挂「复职审批中」标记）
        approvalLauncher.launch(
                () -> buildStart(apply),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                // 发起确认失败：本地单退回「已撤销」，不留下永远审批中的孤儿单
                () -> mapper.update(null, new LambdaUpdateWrapper<RehireApply>()
                        .eq(RehireApply::getId, apply.getId())
                        .eq(RehireApply::getStatus, 0)
                        .set(RehireApply::getStatus, 3)));

        RehireApply fresh = mapper.selectById(apply.getId());
        log.info("复职申请已提交 applyId={} 员工={} 复职日期={} 状态={}",
                apply.getId(), emp.getName(), rehireDate,
                fresh == null ? null : fresh.getStatus());
        return toVO(fresh == null ? apply : fresh, user);
    }

    /** 纯构造发起请求（不含远程调用） */
    private ApprovalStartDTO buildStart(RehireApply apply) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_REHIRE);
        dto.setBizId(String.valueOf(apply.getId()));
        dto.setBizNo(nvl(apply.getEmpNo()));
        dto.setTitle("复职审批 · " + nvl(apply.getEmployeeName()));
        dto.setApplicantId(apply.getApplicantAccountId());
        dto.setApplicantName(nvl(apply.getApplicantName()));
        dto.setApplicantOrgId(apply.getOrgId());
        dto.setOrgPath(nvl(apply.getOrgName()));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(apply.getEmployeeId()));
        form.put("empNo", nvl(apply.getEmpNo()));
        form.put("employeeName", nvl(apply.getEmployeeName()));
        form.put("orgName", nvl(apply.getOrgName()));
        form.put("resignDate", apply.getResignDate() == null ? null : apply.getResignDate().toString());
        form.put("rehireDate", apply.getRehireDate() == null ? null : apply.getRehireDate().toString());
        form.put("leftMonths", leftMonths(apply));
        form.put("reason", nvl(apply.getReason()));
        form.put("accountAction", Integer.valueOf(1).equals(apply.getEnableAccount())
                ? "复职时启用统一账号" : "仅复职，账号保持停用");
        form.put("submitter", nvl(apply.getApplicantName()));
        dto.setFormData(form);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /* ---------------- 终审回调 ---------------- */

    /**
     * 接收审批中心回传的复职终态。<b>必须幂等</b>：回调失败会退避重试。
     * 通过 → 档案复职（清离职信息、按需启用账号）；驳回/撤回/终止 → 只清在途标记。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleRehireApprovalResult(ApprovalCallbackRequest req) {
        Long applyId = parseLongOrNull(req.getBizId());
        Long instId = parseLongOrNull(req.getInstanceId());
        if (applyId == null) {
            log.warn("复职审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        RehireApply apply = mapper.selectById(applyId);
        if (apply == null) {
            log.warn("复职审批回调对应的申请不存在 bizId={}", req.getBizId());
            return;
        }
        if (apply.getApprovalInstanceId() != null && !apply.getApprovalInstanceId().equals(instId)) {
            log.info("忽略过期的复职审批回调 applyId={} 当前单={} 回调单={}",
                    applyId, apply.getApprovalInstanceId(), instId);
            return;
        }
        if (apply.getStatus() == null || apply.getStatus() != 0) {
            log.info("复职审批回调重复投递或状态不匹配，忽略 applyId={} status={}",
                    applyId, apply.getStatus());
            return;
        }
        if (Boolean.TRUE.equals(req.getApproved())) {
            Employee emp = employeeMapper.selectById(apply.getEmployeeId());
            if (emp == null) {
                log.warn("复职审批通过但员工档案已不存在，标记通过 applyId={}", applyId);
                apply.setStatus(1);
            } else if (emp.getStatus() != null && emp.getStatus() != EmployeeStatus.LEFT.getCode()) {
                // 已被其他途径复职：视为处理过，正常返回 200，避免 settings 无意义重试
                log.info("复职审批通过但档案已不在离职状态，按幂等处理 applyId={} status={}",
                        applyId, emp.getStatus());
                apply.setStatus(1);
            } else {
                employeeService.reinstateByApproval(apply.getEmployeeId(),
                        Integer.valueOf(1).equals(apply.getEnableAccount()),
                        "复职审批通过（" + nvl(apply.getReason()) + "）");
                apply.setStatus(1);
            }
        } else {
            apply.setStatus(req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4) ? 3 : 2);
        }
        mapper.updateById(apply);
        clearApprovalMarks(apply);
        log.info("复职审批终审已处理 applyId={} status={} result={}",
                applyId, apply.getStatus(), req.getResultName());
    }

    /** 清掉申请单与员工档案上的在途标记（updateById 跳过 null，必须用 wrapper） */
    private void clearApprovalMarks(RehireApply apply) {
        mapper.update(null, new LambdaUpdateWrapper<RehireApply>()
                .eq(RehireApply::getId, apply.getId())
                .set(RehireApply::getApprovalInstanceId, null));
        if (apply.getEmployeeId() != null) {
            employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                    .eq(Employee::getId, apply.getEmployeeId())
                    .set(Employee::getApprovalInstanceId, null)
                    .set(Employee::getApprovalBizType, null));
        }
    }

    /* ---------------- 撤销 ---------------- */

    /**
     * 撤销复职申请：先撤回审批中心的在途单，再改本地状态。
     * <p>刻意不加 {@code @Transactional}：撤回时 settings 会同步回调本服务，
     * 回调线程要 UPDATE 同一行，持有写锁等响应会互等超时。
     */
    public RehireApplyVO cancel(Long id) {
        SecurityUser user = currentUser();
        RehireApply apply = require(id);
        if (!user.isSuperAdmin() && !user.getAccountId().equals(apply.getApplicantAccountId())) {
            throw new BizException(EhrErrorCode.APPROVAL_NOT_APPLICANT, "只能撤销自己提交的申请");
        }
        if (apply.getStatus() == null || apply.getStatus() != 0) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    "只有审批中的复职申请可以撤销");
        }
        Long runningId = approvalLauncher.latestRunningId(BIZ_REHIRE, String.valueOf(apply.getId()));
        if (runningId != null) {
            withdraw(runningId, user);
        }
        apply.setStatus(3);
        mapper.updateById(apply);
        clearApprovalMarks(apply);
        return toVO(apply, user);
    }

    private void withdraw(Long instanceId, SecurityUser user) {
        ApprovalActionDTO act = new ApprovalActionDTO();
        act.setOperatorId(user.getAccountId());
        act.setOperatorName(user.getUsername());
        act.setNote("发起人撤销复职申请");
        com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp;
        try {
            resp = approvalApi.withdraw(instanceId, act);
        } catch (Exception e) {
            log.error("撤回复职审批单失败 instanceId={}", instanceId, e);
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "撤回审批单失败，请稍后重试");
        }
        if (resp == null || !resp.ok()) {
            String msg = resp == null ? "" : nvl(resp.getMessage());
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    msg.isBlank() ? "撤回审批单失败" : msg);
        }
    }

    /* ---------------- 查询 ---------------- */

    public RehireApplyVO detail(Long id) {
        return toVO(require(id), currentUserOrNull());
    }

    /** scope=mine 我提交的；scope=all 全部（需审批权限） */
    public R.Page<RehireApplyVO> page(String scope, Integer status, int page, int size) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<RehireApply> w = new LambdaQueryWrapper<>();
        if ("all".equalsIgnoreCase(scope)) {
            if (!user.isSuperAdmin()
                    && !user.getPermissions().contains(PermissionCodes.APPROVAL_APPROVE)
                    && !user.getPermissions().contains(PermissionCodes.EMPLOYEE_RESIGN)) {
                throw new BizException(ErrorCode.FORBIDDEN, "没有审批权限，只能查看自己提交的申请");
            }
        } else {
            w.eq(RehireApply::getApplicantAccountId, user.getAccountId());
        }
        if (status != null) {
            w.eq(RehireApply::getStatus, status);
        } else {
            // 已撤销只留档，不在列表展示
            w.ne(RehireApply::getStatus, 3);
        }
        w.orderByDesc(RehireApply::getCreatedAt);
        IPage<RehireApply> ip = mapper.selectPage(new Page<>(page, size), w);
        List<RehireApplyVO> rows = ip.getRecords().stream()
                .map(a -> toVO(a, user))
                .collect(Collectors.toList());
        return R.Page.of(rows, ip.getTotal());
    }

    /* ---------------- 内部工具 ---------------- */

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
    }

    private SecurityUser currentUserOrNull() {
        return SecurityUtils.currentUser().orElse(null);
    }

    private RehireApply require(Long id) {
        RehireApply apply = mapper.selectById(id);
        if (apply == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "复职申请不存在");
        }
        return apply;
    }

    private String applicantName(SecurityUser user) {
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        return me == null ? user.getUsername() : me.getName();
    }

    private long leftMonths(RehireApply a) {
        if (a.getResignDate() == null || a.getRehireDate() == null) {
            return 0L;
        }
        return Math.max(0L, ChronoUnit.MONTHS.between(
                YearMonth.from(a.getResignDate()), YearMonth.from(a.getRehireDate())));
    }

    private RehireApplyVO toVO(RehireApply a, SecurityUser user) {
        RehireApplyVO vo = new RehireApplyVO();
        vo.setId(a.getId());
        vo.setEmployeeId(a.getEmployeeId());
        vo.setEmpNo(a.getEmpNo());
        vo.setEmployeeName(a.getEmployeeName());
        vo.setOrgName(a.getOrgName());
        vo.setResignDate(a.getResignDate() == null ? null : a.getResignDate().toString());
        vo.setRehireDate(a.getRehireDate() == null ? null : a.getRehireDate().toString());
        vo.setLeftMonths(leftMonths(a));
        vo.setReason(a.getReason());
        vo.setEnableAccount(Integer.valueOf(1).equals(a.getEnableAccount()));
        vo.setAccountAction(Integer.valueOf(1).equals(a.getEnableAccount())
                ? "复职并启用账号" : "仅复职，账号保持停用");
        vo.setStatus(a.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(a.getStatus(), "未知"));
        vo.setApplicantName(a.getApplicantName());
        vo.setCreatedAt(a.getCreatedAt() == null ? null : a.getCreatedAt().toString().replace('T', ' '));
        vo.setApprovalInstanceId(a.getApprovalInstanceId());
        vo.setApprovalBizType(a.getApprovalInstanceId() == null ? null : BIZ_REHIRE);
        vo.setMine(user != null && user.getAccountId().equals(a.getApplicantAccountId()));
        return vo;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }
}
