package com.laimengchao.sso.ehr.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.leave.dto.LeaveApplyRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveDelegateOptionsVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveVO;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.entity.OrgType;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.ehr.security.SecurityUser;
import com.laimengchao.sso.ehr.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalDelegateDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import com.laimengchao.sso.settings.client.support.SettingsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 请假服务：提交 / 我的记录 / 撤销 / 接收审批中心终审回调。
 * <p>
 * <b>审批已统一走审批中心</b>：本服务不再解析审批人、不再本地通过/驳回，
 * 提交时向 settings 发起 {@code ehr.leave} 实例并记下实例号，终态由回调写回。
 * 通过/驳回/转办/加签/撤回都在审批中心完成，审批权限由「任务分派给谁」决定。
 * <p>
 * 请假类型仍收口到配置中心主数据 biz_leave_type，配置中心不可用时走内置兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveService {

    /** 请假类型主数据类别（配置中心 biz_leave_type 真表） */
    public static final String BIZ_LEAVE_TYPE = "leave_type";

    /** 请假审批场景编码，对应 settings 的 cfg_biz_scene.biz_type */
    private static final String BIZ_LEAVE = "ehr.leave";
    /** 终审结果接收地址（settings 直连 EHR，不经网关） */
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";

    /** 内置兜底：配置中心不可用 / 未返回数据时，保证请假流程还能跑 */
    private static final Map<String, String> FALLBACK_TYPE_NAMES = Map.of(
            "ANNUAL", "年假",
            "SICK", "病假",
            "PERSONAL", "事假",
            "MARRIAGE", "婚假",
            "MATERNITY", "产假/陪产假",
            "COMPENSATORY", "调休",
            "OTHER", "其他");

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待审批",
            1, "已通过",
            2, "已驳回",
            3, "已撤销");

    private final LeaveMapper leaveMapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final SettingsClient settingsClient;
    private final ApprovalApi approvalApi;
    /** 统一的三段式发起：本地先提交 → 远程发起 → 仅在途才回写实例号 */
    private final ApprovalLauncher approvalLauncher;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /**
     * 当前可用的 请假类型 → 中文名（LinkedHashMap，保持配置中心里的排序）。
     * 审批中心聚合待办时也要用，所以放开成 public。
     */
    public Map<String, String> typeNames() {
        Map<String, String> fromSettings = settingsClient.bizMap(BIZ_LEAVE_TYPE);
        return fromSettings.isEmpty() ? FALLBACK_TYPE_NAMES : fromSettings;
    }

    public String typeName(String type) {
        return typeNames().getOrDefault(type, type);
    }

    public R.Page<LeaveVO> page(String scope, Integer status, int page, int size) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<LeaveRequest> w = new LambdaQueryWrapper<>();
        boolean all = "all".equalsIgnoreCase(scope);
        if (all) {
            if (!user.isSuperAdmin() && !user.getPermissions().contains(PermissionCodes.LEAVE_APPROVE)) {
                throw new BizException(ErrorCode.FORBIDDEN, "没有审批权限，只能查看自己的请假");
            }
        } else {
            w.eq(LeaveRequest::getAccountId, user.getAccountId());
        }
        if (status != null) {
            w.eq(LeaveRequest::getStatus, status);
        } else {
            // 已撤销只留档，不在列表展示
            w.ne(LeaveRequest::getStatus, 3);
        }
        w.orderByDesc(LeaveRequest::getCreatedAt);
        IPage<LeaveRequest> ip = leaveMapper.selectPage(new Page<>(page, size), w);
        List<LeaveVO> rows = ip.getRecords().stream()
                .map(r -> toVO(r, user))
                .collect(Collectors.toList());
        return R.Page.of(rows, ip.getTotal());
    }

    /**
     * 提交请假：<b>三段式</b>——① 本地单据先落库并提交 ② 向审批中心发起实例
     * ③ 仅当实例仍在进行中才回写实例号。
     * <p>
     * 不能把三步塞进同一个 {@code @Transactional}：settings 发起时若模板没解析到审批人 /
     * 条件全假，实例会在 start() 里当场终态，并在事务提交后同步回调本服务；
     * 旧写法下回调线程读不到尚未提交的本地行，按「业务行不存在」正常返回，
     * 审批单标记回调成功、业务表却永远卡在「待审批」。本地先提交后回调必能读到行。
     */
    public LeaveVO apply(LeaveApplyRequest req) {
        SecurityUser user = currentUser();
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "当前账号未关联员工档案，无法提交请假；请联系 HR 在人员管理里建档");
        }
        // 只有已正式入职（试用期 / 在职）且尚未进入离职流程的人能请假
        EmployeeStatus status = EmployeeStatus.of(emp.getStatus());
        if (status == null || !status.isWorking()) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID,
                    "当前状态为「" + (status == null ? "未知" : status.getLabel()) + "」，只有试用期 / 在职员工可以提交请假");
        }
        // 部门负责人请假必须指定审批转办人：请假期间他的审批任务由该人代收
        Long delegateAccountId = resolveDelegate(req.getDelegateAccountId(), emp, user);
        String delegateName = "";
        if (delegateAccountId != null) {
            Employee delegate = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getAccountId, delegateAccountId).last("limit 1"));
            delegateName = nvl(delegate == null ? null : delegate.getName());
        }

        String type = req.getLeaveType().trim().toUpperCase();
        if (!typeNames().containsKey(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请假类型不合法");
        }
        LocalDate start = parseDate(req.getStartDate());
        LocalDate end = parseDate(req.getEndDate());
        if (end.isBefore(start)) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "结束日期不能早于开始日期");
        }
        long dayCount = ChronoUnit.DAYS.between(start, end) + 1;

        LeaveRequest leave = new LeaveRequest();
        leave.setEmployeeId(emp.getId());
        leave.setAccountId(user.getAccountId());
        leave.setApplicantName(emp.getName());
        leave.setLeaveType(type);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setDays(BigDecimal.valueOf(dayCount));
        leave.setReason(req.getReason().trim());
        leave.setDelegateAccountId(delegateAccountId);
        leave.setDelegateName(delegateName);
        leave.setStatus(0);
        // ① 本地写入独立提交：settings 的同步回调发生时，这行一定可见
        approvalLauncher.inTx(() -> leaveMapper.insert(leave));

        // ②③ 远程发起 + 条件回写（发起确认失败时会把这张单自动撤销）
        approvalLauncher.launch(
                () -> buildLeaveStart(leave, emp, user),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                () -> leaveMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getId, leave.getId())
                        .eq(LeaveRequest::getStatus, 0)
                        .set(LeaveRequest::getStatus, 3)
                        .set(LeaveRequest::getApproveNote, "发起审批失败，系统自动撤销")));

        // 实例可能在 start() 内当场终态并同步回调，重新读一次拿真实业务状态
        LeaveRequest fresh = leaveMapper.selectById(leave.getId());
        return toVO(fresh == null ? leave : fresh, user);
    }

    /** 纯构造请假审批发起请求；远程调用与响应兜底统一交给 ApprovalLauncher */
    private ApprovalStartDTO buildLeaveStart(LeaveRequest leave, Employee emp, SecurityUser user) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(BIZ_LEAVE);
        dto.setBizId(String.valueOf(leave.getId()));
        dto.setBizNo(String.valueOf(leave.getId()));
        dto.setTitle("请假审批 · " + nvl(emp.getName()) + " · " + typeName(leave.getLeaveType()));
        dto.setApplicantId(user.getAccountId());
        dto.setApplicantName(nvl(emp.getName()));
        dto.setApplicantOrgId(emp.getOrgId());
        dto.setOrgPath(orgPathOf(emp.getOrgId()));

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(emp.getId()));
        form.put("empNo", nvl(emp.getEmpNo()));
        form.put("name", nvl(emp.getName()));
        form.put("orgPath", orgPathOf(emp.getOrgId()));
        form.put("leaveType", leave.getLeaveType());
        form.put("leaveTypeName", typeName(leave.getLeaveType()));
        form.put("days", leave.getDays());
        form.put("startDate", leave.getStartDate() == null ? null : leave.getStartDate().toString());
        form.put("endDate", leave.getEndDate() == null ? null : leave.getEndDate().toString());
        form.put("reason", nvl(leave.getReason()));
        form.put("delegateName", nvl(leave.getDelegateName()));
        form.put("submitter", nvl(emp.getName()));
        dto.setFormData(form);
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);
        return dto;
    }

    /**
     * 请假审批终审回调。必须幂等：回调失败会退避重试，同一结果可能投递多次。
     * <p>
     * 通过 → 1；驳回 → 2；撤回 / 终止 → 3。两种情况都清掉实例号，
     * 否则列表会一直显示「请假审批中」。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleLeaveApprovalResult(ApprovalCallbackRequest req) {
        Long leaveId = parseLongOrNull(req.getBizId());
        Long instId = parseLongOrNull(req.getInstanceId());
        if (leaveId == null) {
            log.warn("请假审批回调缺少业务ID：{}", req.getBizId());
            return;
        }
        LeaveRequest leave = leaveMapper.selectById(leaveId);
        if (leave == null) {
            log.warn("请假审批回调对应的请假单不存在 bizId={}", req.getBizId());
            return;
        }
        if (leave.getApprovalInstanceId() != null && !leave.getApprovalInstanceId().equals(instId)) {
            log.info("忽略过期的请假审批回调 leaveId={} 当前单={} 回调单={}",
                    leaveId, leave.getApprovalInstanceId(), instId);
            return;
        }
        if (leave.getStatus() == null || leave.getStatus() != 0) {
            log.info("请假审批回调重复投递或状态不匹配，忽略 leaveId={} status={}", leaveId, leave.getStatus());
            return;
        }
        int st;
        if (Boolean.TRUE.equals(req.getApproved())) {
            st = 1;
        } else if (req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4)) {
            st = 3;
        } else {
            st = 2;
        }
        leave.setStatus(st);
        leave.setApproveNote(req.getResultName() == null ? "" : req.getResultName());
        leave.setApprovedAt(LocalDateTime.now());
        leaveMapper.updateById(leave);
        if (st == 1) {
            // 负责人请假通过：按其请假起止时间登记审批委托，期间他的审批任务由转办人代收
            registerLeaveDelegate(leave);
        }
        clearInstance(leaveId);
        log.info("请假审批终审已处理 leaveId={} status={} result={}", leaveId, st, req.getResultName());
    }

    /**
     * 撤销自己的请假：先把审批中心的在途单撤回，再改本地状态。
     * <p>
     * <b>刻意不加 @Transactional</b>：settings 处理撤回时会同步回调本服务，回调线程要
     * UPDATE 同一行 ehr_leave；若本方法持有该行写锁去等 settings 响应，两边会互相等待
     * 直到锁超时。这里先远程撤回（回调里已经把状态写成 3），再幂等地补一次本地写入。
     */
    public LeaveVO cancel(Long id) {
        SecurityUser user = currentUser();
        LeaveRequest leave = require(id);
        if (!user.getAccountId().equals(leave.getAccountId())) {
            throw new BizException(EhrErrorCode.LEAVE_NOT_APPLICANT, "只能撤销自己的请假申请");
        }
        if (leave.getStatus() == null || leave.getStatus() != 0) {
            throw new BizException(EhrErrorCode.LEAVE_STATE_INVALID, "只有待审批的申请可以撤销");
        }
        Long runningId = approvalLauncher.latestRunningId(BIZ_LEAVE, String.valueOf(leave.getId()));
        if (runningId != null) {
            withdrawInstance(runningId, user);
        }
        leave.setStatus(3);
        leaveMapper.updateById(leave);
        clearInstance(id);
        return toVO(leave, user);
    }

    /** 撤回审批中心的在途实例；失败要抛业务错，否则会出现「本地撤销了、审批单还在跑」 */
    private void withdrawInstance(Long instanceId, SecurityUser user) {
        ApprovalActionDTO act = new ApprovalActionDTO();
        act.setOperatorId(user.getAccountId());
        act.setOperatorName(user.getUsername());
        act.setNote("申请人撤销请假");
        com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp;
        try {
            resp = approvalApi.withdraw(instanceId, act);
        } catch (Exception e) {
            log.error("撤回请假审批单失败 instanceId={}", instanceId, e);
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "撤回审批单失败，请稍后重试");
        }
        if (resp == null || !resp.ok()) {
            String msg = resp == null ? "" : nvl(resp.getMessage());
            throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                    msg.isBlank() ? "撤回审批单失败" : msg);
        }
    }

    /** updateById 跳过 null 字段，清实例号必须用 UpdateWrapper 显式 set null */
    private void clearInstance(Long leaveId) {
        leaveMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                .eq(LeaveRequest::getId, leaveId)
                .set(LeaveRequest::getApprovalInstanceId, null));
    }

    /**
     * 请假弹窗的「审批转办」选项：当前登录人是部门负责人时该字段必填，
     * 候选人为在职且有账号的其他员工（带组织名，方便同名区分）。
     */
    public LeaveDelegateOptionsVO delegateOptions() {
        SecurityUser user = currentUser();
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        LeaveDelegateOptionsVO vo = new LeaveDelegateOptionsVO();
        if (me == null || !isDepartmentLeader(me)) {
            return vo;
        }
        vo.setRequired(true);
        Map<Long, String> orgNames = new HashMap<>();
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            orgNames.put(n.getId(), nvl(n.getName()));
        }
        for (Employee e : employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .ne(Employee::getId, me.getId())
                .isNotNull(Employee::getAccountId)
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode())
                .orderByAsc(Employee::getName))) {
            LeaveDelegateOptionsVO.Option o = new LeaveDelegateOptionsVO.Option();
            o.setAccountId(e.getAccountId());
            o.setName(nvl(e.getName()));
            o.setOrgName(orgNames.getOrDefault(e.getOrgId(), ""));
            vo.getOptions().add(o);
        }
        return vo;
    }

    /** 部门负责人请假必填转办人；普通人填了就校验，不填就不登记委托 */
    private Long resolveDelegate(Long raw, Employee applicant, SecurityUser user) {
        if (raw == null) {
            if (isDepartmentLeader(applicant)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "部门负责人请假必须指定审批转办人");
            }
            return null;
        }
        if (raw.equals(user.getAccountId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "审批转办人不能是自己");
        }
        Employee delegate = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, raw).last("limit 1"));
        if (delegate == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "指定的审批转办人不存在或未开通账号");
        }
        EmployeeStatus ds = EmployeeStatus.of(delegate.getStatus());
        if (ds == null || !ds.isWorking()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "审批转办人必须是试用期 / 在职员工");
        }
        return raw;
    }

    /** 是否是「部门」负责人（ehr_org_node.leader_employee_id 指向本人） */
    private boolean isDepartmentLeader(Employee emp) {
        if (emp == null || emp.getId() == null) {
            return false;
        }
        return orgMapper.selectCount(new LambdaQueryWrapper<OrgNode>()
                .eq(OrgNode::getLeaderEmployeeId, emp.getId())
                .eq(OrgNode::getType, OrgType.DEPARTMENT.name())) > 0;
    }

    /**
     * 请假通过后登记审批委托：窗口 = 请假起止日，期间该负责人的审批任务直接派给转办人。
     * remark 作幂等键，回调重复投递只更新时间段。
     */
    private void registerLeaveDelegate(LeaveRequest leave) {
        if (leave.getDelegateAccountId() == null
                || leave.getStartDate() == null || leave.getEndDate() == null) {
            return;
        }
        ApprovalDelegateDTO dto = new ApprovalDelegateDTO();
        dto.setOwnerId(leave.getAccountId());
        dto.setOwnerName(nvl(leave.getApplicantName()));
        dto.setDelegateId(leave.getDelegateAccountId());
        dto.setDelegateName(nvl(leave.getDelegateName()));
        // 空 = 全部业务：请假期间部门内所有需要负责人审的单子都由转办人代收
        dto.setBizType("");
        dto.setStartTime(leave.getStartDate().atStartOfDay());
        dto.setEndTime(leave.getEndDate().atTime(23, 59, 59));
        dto.setRemark("leave-delegate#" + leave.getId());
        try {
            approvalApi.delegate(dto);
            log.info("请假审批通过，已登记审批委托 leaveId={} 负责人={} 转办人={} {}~{}",
                    leave.getId(), leave.getAccountId(), leave.getDelegateAccountId(),
                    leave.getStartDate(), leave.getEndDate());
        } catch (Exception e) {
            // 委托登记失败不影响请假结果，可在审批中心手工补登委托
            log.error("登记请假审批委托失败 leaveId={} delegateAccountId={}",
                    leave.getId(), leave.getDelegateAccountId(), e);
        }
    }

    /* ---------------- 内部工具 ---------------- */

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() ->
                new BizException(ErrorCode.UNAUTHORIZED));
    }

    private LeaveRequest require(Long id) {
        LeaveRequest leave = leaveMapper.selectById(id);
        if (leave == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "请假申请不存在");
        }
        return leave;
    }

    private LocalDate parseDate(String raw) {
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    /** 组织全路径，如「集团 / 子公司 / 研发部」；节点不存在返回空串 */
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
        java.util.List<String> names = new java.util.ArrayList<>();
        if (node.getPath() != null) {
            for (String part : node.getPath().split("/")) {
                if (part.isBlank()) continue;
                try {
                    OrgNode p = orgs.get(Long.valueOf(part));
                    if (p != null) names.add(nvl(p.getName()));
                } catch (NumberFormatException ignored) {
                    // 脏 path 跳过
                }
            }
        }
        if (names.isEmpty()) names.add(nvl(node.getName()));
        return String.join(" / ", names);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private Long parseLongOrNull(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LeaveVO toVO(LeaveRequest r, SecurityUser user) {
        LeaveVO vo = new LeaveVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setAccountId(r.getAccountId());
        vo.setApplicantName(r.getApplicantName());
        vo.setLeaveType(r.getLeaveType());
        vo.setLeaveTypeName(typeName(r.getLeaveType()));
        vo.setStartDate(r.getStartDate() == null ? null : r.getStartDate().toString());
        vo.setEndDate(r.getEndDate() == null ? null : r.getEndDate().toString());
        vo.setDays(r.getDays());
        vo.setReason(r.getReason());
        vo.setStatus(r.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(r.getStatus(), "未知"));
        vo.setApproverAccountId(r.getApproverAccountId());
        vo.setApproverName(r.getApproverName());
        vo.setDelegateAccountId(r.getDelegateAccountId());
        vo.setDelegateName(r.getDelegateName());
        vo.setApproveNote(r.getApproveNote());
        vo.setApprovedAt(r.getApprovedAt() == null ? null
                : r.getApprovedAt().toString().replace('T', ' '));
        vo.setCreatedAt(r.getCreatedAt() == null ? null
                : r.getCreatedAt().toString().replace('T', ' '));
        Long me = user.getAccountId();
        vo.setMine(me != null && me.equals(r.getAccountId()));
        // 审批动作统一走审批中心，本地不再判断「轮到我审」
        vo.setActionable(false);
        vo.setApprovalInstanceId(r.getApprovalInstanceId());
        vo.setApprovalBizType(r.getApprovalInstanceId() == null ? null : BIZ_LEAVE);
        return vo;
    }
}
