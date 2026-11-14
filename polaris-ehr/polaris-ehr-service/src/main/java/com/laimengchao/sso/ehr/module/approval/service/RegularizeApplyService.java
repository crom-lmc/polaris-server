package com.laimengchao.sso.ehr.module.approval.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.approval.dto.RegularizeApplyRequest;
import com.laimengchao.sso.ehr.module.approval.entity.RegularizeApply;
import com.laimengchao.sso.ehr.module.approval.mapper.RegularizeApplyMapper;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.approval.vo.RegularizeApplyVO;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.api.CodeRuleApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 转正申请服务（试用期 → 正式员工）。
 * <p>
 * <b>为什么要有这张申请表</b>：以前「提交转正申请」是直接对 {@code ehr_employee} 打标记并发起审批，
 * 申请人没机会表达自己（试用期做了什么、业绩如何、为什么该转正），审批人只能看档案上的静态字段；
 * 而且驳回后重提不留历史。现在申请内容落到 {@code ehr_regularize_apply}，
 * 每次提交一条独立记录，与离职（{@code ehr_resign_apply}）、复职（{@code ehr_rehire_apply}）同一套模式。
 * <p>
 * <b>审批统一走 settings 审批中心</b>：{@code ehr.regularize} 模板决定几步、谁审。
 * 本服务只做三件事——提交（写申请单 + 发起实例）、收终审回调、撤销。
 * <p>
 * 三段式发起由 {@link ApprovalLauncher} 托管：本地申请单先独立提交，再远程 start，
 * 只有实例仍在途才回写实例号。原因见 ApprovalLauncher 的类注释（同步回调竞态）。
 * <p>
 * <b>业务 ID 语义</b>：审批实例的 bizId 是<b>申请单 ID</b>（不是员工 ID）。
 * 员工 ID 放在 formData.employeeId 与申请单上。这样一张员工多次提交时，
 * 每张申请单都对应独立的审批实例，互不干扰。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegularizeApplyService {

    /** 转正审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    public static final String BIZ_REGULARIZE = "ehr.regularize";
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";
    /** 单号规则对应的业务表名：配置中心「单号规则」按表名识别 */
    private static final String TABLE_REGULARIZE = "ehr_regularize_apply";

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "审批中",
            1, "已通过",
            2, "已驳回",
            3, "已撤销");

    private final RegularizeApplyMapper mapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final ApprovalApi approvalApi;
    private final ApprovalLauncher approvalLauncher;
    /** 配置中心取号接口：生成申请单号 */
    private final CodeRuleApi codeRuleApi;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /* ---------------- 提交 ---------------- */

    /**
     * 提交转正申请。刻意不加 {@code @Transactional}：
     * 本地写入由 {@code approvalLauncher.inTx} 独立提交，远程发起必须在其之外。
     *
     * @param employeeId 被转正的员工档案ID
     * @param req        申请人填写的内容（可为空，老前端只点一个按钮也能提交）
     */
    public RegularizeApplyVO submit(Long employeeId, RegularizeApplyRequest req) {
        SecurityUser user = currentUser();
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND);
        }
        if (!EmployeeStatus.PROBATION.equals(EmployeeStatus.of(emp.getStatus()))) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID, "只有「试用期」员工可以提交转正申请");
        }
        if (approvalLauncher.hasRunning(String.valueOf(emp.getId()), null)) {
            throw new BizException(EhrErrorCode.APPROVAL_DUPLICATED, "该员工已有在审批中的单据，请先处理完再提交转正申请");
        }
        long pending = mapper.selectCount(new LambdaQueryWrapper<RegularizeApply>()
                .eq(RegularizeApply::getEmployeeId, employeeId)
                .eq(RegularizeApply::getStatus, 0));
        if (pending > 0) {
            throw new BizException(EhrErrorCode.APPROVAL_DUPLICATED,
                    "该员工已有一张审批中的转正申请，请先处理完再提交");
        }
        // 只有本人能提交。
        // <p>
        // 转正申请的核心内容是自评、业绩、理由——这些只能由员工本人写，
        // HR 代填没有意义（也不该让 HR 绕过本人把试用期员工直接转正）。
        // 因此这里不放开 HR 通道：前端的按钮也只对本人显示，两端口径一致。
        boolean self = user.getAccountId() != null && user.getAccountId().equals(emp.getAccountId());
        if (!self) {
            throw new BizException(ErrorCode.FORBIDDEN, "转正申请只能由员工本人提交");
        }

        LocalDate regularizeDate = parseDate(req == null ? null : req.getRegularizeDate());
        if (regularizeDate == null) {
            // 没填就按预计转正日期兜底，再兜底今天
            regularizeDate = emp.getExpectedRegularizeDate() != null
                    ? emp.getExpectedRegularizeDate() : LocalDate.now();
        }
        if (emp.getExpectedRegularizeDate() != null && regularizeDate.isBefore(emp.getExpectedRegularizeDate())) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "申请转正日期不能早于预计转正日期（" + emp.getExpectedRegularizeDate() + "）");
        }

        OrgNode org = emp.getOrgId() == null ? null : orgMapper.selectById(emp.getOrgId());
        RegularizeApply apply = new RegularizeApply();
        apply.setEmployeeId(emp.getId());
        apply.setEmpNo(emp.getEmpNo());
        // 申请单号：提交前调用配置中心「单号规则」取号，未配置时留空不阻断
        apply.setApplyNo(nextApplyNo(TABLE_REGULARIZE));
        apply.setEmployeeName(emp.getName());
        apply.setOrgId(emp.getOrgId());
        apply.setOrgName(org == null ? "" : nvl(org.getName()));
        apply.setOrgPath(orgPathOf(emp.getOrgId()));
        apply.setPosition(nvl(emp.getPosition()));
        apply.setHireDate(emp.getHireDate());
        apply.setProbationMonths(Boolean.TRUE.equals(emp.getExemptProbation())
                ? 0 : emp.getProbationMonths());
        apply.setExpectedRegularizeDate(emp.getExpectedRegularizeDate());
        apply.setRegularizeDate(regularizeDate);
        apply.setSelfReview(trim(req == null ? null : req.getSelfReview(), 500));
        apply.setWorkSummary(trim(req == null ? null : req.getWorkSummary(), 1000));
        apply.setReason(trim(req == null ? null : req.getReason(), 500));
        apply.setNextPlan(trim(req == null ? null : req.getNextPlan(), 1000));
        apply.setStatus(0);
        apply.setApplicantAccountId(user.getAccountId());
        apply.setApplicantName(applicantName(user));

        // ① 本地写入先独立提交：settings 的同步回调发生时这张单一定可见
        approvalLauncher.inTx(() -> mapper.insert(apply));

        // ② 员工档案打「转正审批中」标记（与老逻辑一致，人员列表据此显示状态）
        approvalLauncher.inTx(() -> employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, emp.getId())
                .set(Employee::getRegularizeStatus, 1)));

        // ③ 远程发起（实例号已退役，不再回写）
        approvalLauncher.launch(
                () -> buildStart(apply),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                // 发起确认失败：本地单退回「已撤销」，员工标记回退，允许重新提交
                () -> {
                    mapper.update(null, new LambdaUpdateWrapper<RegularizeApply>()
                            .eq(RegularizeApply::getId, apply.getId())
                            .eq(RegularizeApply::getStatus, 0)
                            .set(RegularizeApply::getStatus, 3));
                    employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                            .eq(Employee::getId, emp.getId())
                            .eq(Employee::getRegularizeStatus, 1)
                            .set(Employee::getRegularizeStatus, -1));
                });

        RegularizeApply fresh = mapper.selectById(apply.getId());
        log.info("转正申请已提交 applyId={} 员工={} 拟转正日期={} 状态={}",
                apply.getId(), emp.getName(), regularizeDate,
                fresh == null ? null : fresh.getStatus());
        return toVO(fresh == null ? apply : fresh, user);
    }

    /**
     * 取申请单号：跨服务调用失败或未配置规则时直接抛出，不吞异常。
     * 单号是单据的必备标识，宁可提交失败也不能落一条没有编号的申请单。
     */
    private String nextApplyNo(String tableName) {
        // 用 var：settings-client 的 R 与 common.R 是两个类型，只取 data 避免显式类型冲突
        var res = codeRuleApi.next(tableName);
        String no = res == null || res.getData() == null ? "" : res.getData().trim();
        if (no.isEmpty()) {
            throw new IllegalStateException("取申请单号失败：未配置单号规则或返回为空，tableName=" + tableName);
        }
        return no;
    }

    /** 纯构造发起请求（不含远程调用） */
    private ApprovalStartDTO buildStart(RegularizeApply apply) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_REGULARIZE);
        dto.setBizId(String.valueOf(apply.getId()));
        // 审批中心「业务编码」用申请单号；未配置规则时回退工号，保证仍有标识
        dto.setBizNo(nvl(apply.getApplyNo()).isEmpty() ? nvl(apply.getEmpNo()) : nvl(apply.getApplyNo()));
        dto.setTitle("转正审批 · " + nvl(apply.getEmployeeName()));
        dto.setApplicantId(apply.getApplicantAccountId());
        dto.setApplicantName(nvl(apply.getApplicantName()));
        dto.setApplicantOrgId(apply.getOrgId());
        dto.setOrgPath(nvl(apply.getOrgPath()));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(apply.getEmployeeId()));
        form.put("empNo", nvl(apply.getEmpNo()));
        form.put("applyNo", nvl(apply.getApplyNo()));
        form.put("name", nvl(apply.getEmployeeName()));
        form.put("orgPath", nvl(apply.getOrgPath()));
        form.put("position", nvl(apply.getPosition()));
        form.put("hireDate", apply.getHireDate() == null ? null : apply.getHireDate().toString());
        form.put("probationMonths", apply.getProbationMonths());
        form.put("expectedRegularizeDate", apply.getExpectedRegularizeDate() == null
                ? null : apply.getExpectedRegularizeDate().toString());
        form.put("regularizeDate", apply.getRegularizeDate() == null
                ? null : apply.getRegularizeDate().toString());
        form.put("selfReview", nvl(apply.getSelfReview()));
        form.put("workSummary", nvl(apply.getWorkSummary()));
        form.put("reason", nvl(apply.getReason()));
        form.put("nextPlan", nvl(apply.getNextPlan()));
        form.put("probationText", probationSummary(apply));
        form.put("submitter", nvl(apply.getApplicantName()));
        dto.setFormData(form);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /* ---------------- 终审回调 ---------------- */

    /**
     * 接收审批中心回传的转正终态。<b>必须幂等</b>：回调失败会退避重试。
     * 通过 → 档案转正式在职（40）+ regularizeStatus=3；驳回/撤回/终止 → 留在试用期、regularizeStatus=-1。
     * <p>
     * 兼容两种 bizId：
     * <ul>
     *   <li><b>申请单 ID</b>（新逻辑）：按 {@link RegularizeApply} 定位。</li>
     *   <li><b>员工 ID</b>（老逻辑）：改造前的转正实例 bizId 就是 employeeId，
     *       这里按 employeeId 回落处理，保证存量在途单不会卡住。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleRegularizeApprovalResult(ApprovalCallbackRequest req) {
        Long bizId = parseLongOrNull(req.getBizId());
        Long instId = parseLongOrNull(req.getInstanceId());
        if (bizId == null) {
            log.warn("转正审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        // 存量单（bizIdKind 为空 / SUBJECT）的 bizId 就是员工 ID，走老分支；
        // DOC 单按 bizId 精确定位申请单，绝不再「selectById 查不到就猜老单」。
        if (!req.docBizId()) {
            handleLegacyCallback(req, bizId);
            return;
        }
        RegularizeApply apply = mapper.selectById(bizId);
        if (apply == null) {
            log.warn("转正审批回调对应的申请单不存在 bizId={}", req.getBizId());
            return;
        }
        if (apply.getApprovalInstanceId() != null && !apply.getApprovalInstanceId().equals(instId)) {
            log.info("忽略过期的转正审批回调 applyId={} 当前单={} 回调单={}",
                    apply.getId(), apply.getApprovalInstanceId(), instId);
            return;
        }
        if (apply.getStatus() == null || apply.getStatus() != 0) {
            log.info("转正审批回调重复投递或状态不匹配，忽略 applyId={} status={}",
                    apply.getId(), apply.getStatus());
            return;
        }
        boolean approved = Boolean.TRUE.equals(req.getApproved());
        apply.setStatus(approved ? 1 : (isWithdrawn(req) ? 3 : 2));
        mapper.updateById(apply);
        applyOutcome(apply.getEmployeeId(), approved, req.getResultName());
        clearApprovalMarks(apply);
        log.info("转正审批终审已处理 applyId={} status={} result={}",
                apply.getId(), apply.getStatus(), req.getResultName());
    }

    /**
     * 兼容改造前的实例：bizId 是员工 ID，没有申请单。
     * 员工档案上的 regularizeStatus 仍是老语义，这里照旧处理。
     */
    private void handleLegacyCallback(ApprovalCallbackRequest req, Long empId) {
        Employee emp = employeeMapper.selectById(empId);
        if (emp == null) {
            log.warn("转正审批回调对应的员工不存在 bizId={}", empId);
            return;
        }
        if (!EmployeeStatus.PROBATION.equals(EmployeeStatus.of(emp.getStatus()))
                || emp.getRegularizeStatus() == null || emp.getRegularizeStatus() != 1) {
            log.info("转正审批回调重复投递或状态不匹配，忽略 employeeId={} status={} regularizeStatus={}",
                    empId, emp.getStatus(), emp.getRegularizeStatus());
            return;
        }
        boolean approved = Boolean.TRUE.equals(req.getApproved());
        emp.setRegularizeStatus(approved ? 3 : -1);
        if (approved) {
            emp.setStatus(EmployeeStatus.ACTIVE.getCode());
        }
        employeeMapper.updateById(emp);
        log.info("转正审批终审已处理（存量实例）employeeId={} approved={} result={}",
                empId, approved, req.getResultName());
    }

    /**
     * 终审结果落到员工档案。
     * <p>
     * 注意：这里只更新状态相关的列，<b>不能</b>用 {@code updateById(emp)} 整行回写——
     * 内存里那份 emp 是回调前的旧快照，回写会覆盖掉刚才写入的结果。
     */
    private void applyOutcome(Long employeeId, boolean approved, String resultName) {
        if (employeeId == null) {
            return;
        }
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null) {
            log.warn("转正审批通过但员工档案已不存在 employeeId={}", employeeId);
            return;
        }
        if (approved) {
            employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                    .eq(Employee::getId, employeeId)
                    .set(Employee::getRegularizeStatus, 3)
                    .set(Employee::getStatus, EmployeeStatus.ACTIVE.getCode())
                    .set(Employee::getRegularizeApprovals, appendSystemRecord(
                            emp.getRegularizeApprovals(), true, "审批中心终审通过")));
        } else {
            employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                    .eq(Employee::getId, employeeId)
                    .set(Employee::getRegularizeStatus, -1)
                    .set(Employee::getRegularizeApprovals, appendSystemRecord(
                            emp.getRegularizeApprovals(), false,
                            resultName == null ? "审批未通过" : resultName)));
        }
    }

    /* ---------------- 撤销 ---------------- */

    /**
     * 撤销转正申请：先撤回审批中心的在途单，再改本地状态。
     * <p>刻意不加 {@code @Transactional}：撤回时 settings 会同步回调本服务，
     * 回调线程要 UPDATE 同一行，持有写锁等响应会互等超时。
     */
    public RegularizeApplyVO cancel(Long id) {
        SecurityUser user = currentUser();
        RegularizeApply apply = require(id);
        if (!user.isSuperAdmin() && !user.getAccountId().equals(apply.getApplicantAccountId())) {
            throw new BizException(EhrErrorCode.APPROVAL_NOT_APPLICANT, "只能撤销自己提交的申请");
        }
        if (apply.getStatus() == null || apply.getStatus() != 0) {
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    "只有审批中的转正申请可以撤销");
        }
        Long runningId = approvalLauncher.latestRunningId(BIZ_REGULARIZE, String.valueOf(apply.getId()));
        if (runningId != null) {
            withdraw(runningId, user);
        }
        apply.setStatus(3);
        mapper.updateById(apply);
        clearApprovalMarks(apply);
        employeeMapper.update(null, new LambdaUpdateWrapper<Employee>()
                .eq(Employee::getId, apply.getEmployeeId())
                .eq(Employee::getRegularizeStatus, 1)
                .set(Employee::getRegularizeStatus, -1));
        return toVO(apply, user);
    }

    private void withdraw(Long instanceId, SecurityUser user) {
        ApprovalActionDTO act = new ApprovalActionDTO();
        act.setOperatorId(user.getAccountId());
        act.setOperatorName(user.getUsername());
        act.setNote("发起人撤销转正申请");
        com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp;
        try {
            resp = approvalApi.withdraw(instanceId, act);
        } catch (Exception e) {
            log.error("撤回调正审批单失败 instanceId={}", instanceId, e);
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "撤回审批单失败，请稍后重试");
        }
        if (resp == null || !resp.ok()) {
            String msg = resp == null ? "" : nvl(resp.getMessage());
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    msg.isBlank() ? "撤回审批单失败" : msg);
        }
    }

    /* ---------------- 查询 ---------------- */

    public RegularizeApplyVO detail(Long id) {
        return toVO(require(id), currentUserOrNull());
    }

    /** scope=mine 我提交的；scope=all 全部（需审批权限） */
    public R.Page<RegularizeApplyVO> page(String scope, Integer status, int page, int size) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<RegularizeApply> w = new LambdaQueryWrapper<>();
        if ("all".equalsIgnoreCase(scope)) {
            if (!user.isSuperAdmin()
                    && !user.getPermissions().contains(PermissionCodes.APPROVAL_APPROVE)
                    && !user.getPermissions().contains(PermissionCodes.EMPLOYEE_EDIT)) {
                throw new BizException(ErrorCode.FORBIDDEN, "没有审批权限，只能查看自己提交的申请");
            }
        } else {
            w.eq(RegularizeApply::getApplicantAccountId, user.getAccountId());
        }
        if (status != null) {
            w.eq(RegularizeApply::getStatus, status);
        } else {
            // 已撤销只留档，不在列表展示
            w.ne(RegularizeApply::getStatus, 3);
        }
        w.orderByDesc(RegularizeApply::getCreatedAt);
        IPage<RegularizeApply> ip = mapper.selectPage(new Page<>(page, size), w);
        List<RegularizeApplyVO> rows = ip.getRecords().stream()
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

    private RegularizeApply require(Long id) {
        RegularizeApply apply = mapper.selectById(id);
        if (apply == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "转正申请不存在");
        }
        return apply;
    }

    private String applicantName(SecurityUser user) {
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        return me == null ? user.getUsername() : me.getName();
    }

    /** 清掉申请单上的在途审批标记（updateById 跳过 null，必须用 wrapper） */
    private void clearApprovalMarks(RegularizeApply apply) {
        mapper.update(null, new LambdaUpdateWrapper<RegularizeApply>()
                .eq(RegularizeApply::getId, apply.getId())
                .set(RegularizeApply::getApprovalInstanceId, null));
    }

    /** result=3 撤销 / 4 终止 → 算「已撤销」，其余未通过都算「已驳回」 */
    private boolean isWithdrawn(ApprovalCallbackRequest req) {
        Integer r = req.getResult();
        return r != null && (r == 3 || r == 4);
    }

    /** 组织全路径，如「集团 / 子公司 / 研发部」 */
    private String orgPathOf(Long orgId) {
        if (orgId == null) {
            return "";
        }
        Map<Long, OrgNode> orgs = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgs.put(n.getId(), n);
        }
        OrgNode node = orgs.get(orgId);
        if (node == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        OrgNode cur = node;
        while (cur != null) {
            names.add(0, cur.getName());
            cur = cur.getParentId() == null ? null : orgs.get(cur.getParentId());
        }
        return String.join(" / ", names);
    }

    /** 「6 个月 · 预计 2027-05-10 转正」 */
    private String probationSummary(RegularizeApply a) {
        if (a.getProbationMonths() != null && a.getProbationMonths() == 0) {
            return "免试用期 · 入职直接转正";
        }
        if (a.getProbationMonths() == null) {
            return "";
        }
        String base = a.getProbationMonths() + " 个月";
        return a.getExpectedRegularizeDate() == null ? base
                : base + " · 预计 " + a.getExpectedRegularizeDate() + " 转正";
    }

    /** 追加一条「审批中心」的系统审批记录（与员工档案里的历史记录同格式） */
    private String appendSystemRecord(String existing, boolean pass, String note) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.core.type.TypeReference<List<com.laimengchao.sso.ehr.module.employee.constant.ApprovalRecord>> type =
                    new com.fasterxml.jackson.core.type.TypeReference<>() { };
            List<com.laimengchao.sso.ehr.module.employee.constant.ApprovalRecord> list =
                    existing == null || existing.isBlank() ? new ArrayList<>() : om.readValue(existing, type);
            list.add(new com.laimengchao.sso.ehr.module.employee.constant.ApprovalRecord(
                    "SYS", "审批中心", null, "审批中心", pass, nvl(note),
                    java.time.LocalDateTime.now().toString().replace('T', ' ')));
            return om.writeValueAsString(list);
        } catch (Exception e) {
            return existing == null ? "[]" : existing;
        }
    }

    private RegularizeApplyVO toVO(RegularizeApply a, SecurityUser user) {
        RegularizeApplyVO vo = new RegularizeApplyVO();
        vo.setId(a.getId());
        vo.setEmployeeId(a.getEmployeeId());
        vo.setEmpNo(a.getEmpNo());
        vo.setApplyNo(a.getApplyNo());
        vo.setEmployeeName(a.getEmployeeName());
        vo.setOrgName(a.getOrgName());
        vo.setOrgPath(a.getOrgPath());
        vo.setPosition(a.getPosition());
        vo.setHireDate(a.getHireDate() == null ? null : a.getHireDate().toString());
        vo.setProbationMonths(a.getProbationMonths());
        vo.setExpectedRegularizeDate(a.getExpectedRegularizeDate() == null
                ? null : a.getExpectedRegularizeDate().toString());
        vo.setRegularizeDate(a.getRegularizeDate() == null ? null : a.getRegularizeDate().toString());
        vo.setSelfReview(a.getSelfReview());
        vo.setWorkSummary(a.getWorkSummary());
        vo.setReason(a.getReason());
        vo.setNextPlan(a.getNextPlan());
        vo.setStatus(a.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(a.getStatus(), "未知"));
        vo.setApplicantName(a.getApplicantName());
        vo.setCreatedAt(a.getCreatedAt() == null ? null : a.getCreatedAt().toString().replace('T', ' '));
        vo.setApprovalInstanceId(a.getApprovalInstanceId());
        vo.setApprovalBizType(a.getApprovalInstanceId() == null ? null : BIZ_REGULARIZE);
        vo.setMine(user != null && user.getAccountId().equals(a.getApplicantAccountId()));
        return vo;
    }

    private String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
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
