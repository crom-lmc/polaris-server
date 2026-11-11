package com.laimengchao.sso.ehr.module.leave.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.constant.PermissionCodes;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.attachment.entity.Attachment;
import com.laimengchao.sso.ehr.module.attachment.service.AttachmentService;
import com.laimengchao.sso.ehr.module.leave.dto.EarlyReturnRequest;
import com.laimengchao.sso.ehr.module.leave.dto.LeaveApplyRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveAttachment;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveTypeRule;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveAttachmentMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveAttachmentVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveDelegateOptionsVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeavePolicyVO;
import com.laimengchao.sso.ehr.module.leave.vo.LeaveVO;
import com.laimengchao.sso.ehr.module.org.entity.OrgNode;
import com.laimengchao.sso.ehr.module.org.entity.OrgType;
import com.laimengchao.sso.ehr.module.org.mapper.OrgNodeMapper;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
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
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            "MATERNITY", "产假",
            "PATERNITY", "陪产假",
            "COMPENSATORY", "调休",
            "OTHER", "其他");

    /** 证明门槛折算小时用的标准工作时长（默认 8 小时/天） */
    private static final BigDecimal DEFAULT_WORK_HOURS = new BigDecimal("8");

    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待审批",
            1, "已通过",
            2, "已驳回",
            3, "已撤销",
            4, "已销假");

    private final LeaveMapper leaveMapper;
    private final EmployeeMapper employeeMapper;
    private final OrgNodeMapper orgMapper;
    private final SettingsClient settingsClient;
    private final ApprovalApi approvalApi;
    /** 统一的三段式发起：本地先提交 → 远程发起 → 仅在途才回写实例号 */
    private final ApprovalLauncher approvalLauncher;
    private final LeaveTypeRuleService ruleService;
    private final LeaveDaysCalculator daysCalculator;
    private final LeaveCityPolicyService cityPolicyService;
    private final LeaveQuotaService quotaService;
    private final AttachmentService attachmentService;
    private final LeaveAttachmentMapper attachmentMapper;
    private final BizNoService bizNoService;

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

    /** 当前员工所在城市的婚假 / 产假 / 陪产假政策（前端据此锁定天数与计算方式） */
    public LeavePolicyVO cityPolicy() {
        Employee emp = currentEmployee();
        String city = cityPolicyService.cityOf(emp);
        LeavePolicyVO vo = new LeavePolicyVO();
        vo.setCity(city);
        cityPolicyService.policies(city).forEach((type, policy) ->
                vo.getPolicies().put(type, new LeavePolicyVO.Policy(policy.days(), policy.dayMode())));
        return vo;
    }

    /** 按开始日期试算结束日期：城市政策类假期天数固定，由开始日期自动带出结束日期 */
    public LeavePolicyVO plan(String typeRaw, String startDateRaw) {
        Employee emp = currentEmployee();
        String type = typeRaw == null ? "" : typeRaw.trim().toUpperCase();
        LeaveCityPolicyService.CityPolicy policy = cityPolicyService.supports(type)
                ? cityPolicyService.policy(emp, type) : null;
        if (policy == null) {
            String city = cityPolicyService.cityOf(emp);
            throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID,
                    (city == null || city.isBlank() ? "当前员工未配置所属城市" : "城市「" + city + "」")
                            + "未配置" + typeName(type) + "天数，请联系 HR 在埋点配置中维护");
        }
        LeavePolicyVO vo = new LeavePolicyVO();
        vo.setCity(policy.city());
        vo.setType(policy.type());
        vo.setDays(policy.days());
        vo.setDayMode(policy.dayMode());
        vo.setEndDate(daysCalculator.endDateFor(parseDate(startDateRaw), policy.days(), policy.dayMode()));
        return vo;
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
        }
        w.orderByDesc(LeaveRequest::getCreatedAt);
        IPage<LeaveRequest> ip = leaveMapper.selectPage(new Page<>(page, size), w);
        List<LeaveVO> rows = ip.getRecords().stream()
                .map(r -> toVO(r, user, false))
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
        Employee emp = currentEmployee();
        // 只有已正式入职（试用期 / 在职）且尚未进入离职流程的人能请假
        EmployeeStatus status = EmployeeStatus.of(emp.getStatus());
        if (status == null || !status.isWorking()) {
            throw new BizException(EhrErrorCode.EMP_STATUS_INVALID,
                    "当前状态为「" + (status == null ? "未知" : status.getLabel()) + "」，只有试用期 / 在职员工可以提交请假");
        }

        String type = req.getLeaveType().trim().toUpperCase();
        if (!typeNames().containsKey(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请假类型不合法");
        }
        LeaveTypeRule rule = ruleService.get(type);
        if (!Integer.valueOf(1).equals(rule.getEnabled())) {
            throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID, "该请假类型已停用");
        }
        // 城市政策类假期（婚假 / 产假 / 陪产假）：天数与计算方式由所属二级单位城市决定
        LeaveCityPolicyService.CityPolicy cityPolicy = null;
        if (cityPolicyService.supports(type)) {
            cityPolicy = cityPolicyService.policy(emp, type);
            if (cityPolicy == null) {
                String city = cityPolicyService.cityOf(emp);
                throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID,
                        (city == null || city.isBlank() ? "当前员工未配置所属城市" : "城市「" + city + "」")
                                + "未配置" + typeName(type) + "天数，请联系 HR 在埋点配置中维护");
            }
            // 产假仅女性、陪产假仅男性
            if (LeaveCityPolicyService.TYPE_MATERNITY.equals(type)
                    && !Integer.valueOf(2).equals(emp.getGender())) {
                throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID,
                        emp.getGender() == null ? "员工档案性别未知，请先完善身份证信息" : "产假仅限女性员工申请");
            }
            if (LeaveCityPolicyService.TYPE_PATERNITY.equals(type)
                    && !Integer.valueOf(1).equals(emp.getGender())) {
                throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID,
                        emp.getGender() == null ? "员工档案性别未知，请先完善身份证信息" : "陪产假仅限男性员工申请");
            }
        }
        LocalDate start = parseDate(req.getStartDate());
        LocalDate end = parseDate(req.getEndDate());
        String startPeriod = periodOr(req.getStartPeriod(), LeaveDaysCalculator.PERIOD_AM);
        String endPeriod = periodOr(req.getEndPeriod(), LeaveDaysCalculator.PERIOD_PM);
        String startTime = trimToNull(req.getStartTime());
        String endTime = trimToNull(req.getEndTime());
        // 天数默认按工作日历（跳过周末 / 节假日）；历史单据未存 dayMode 时仍按自然日兜底
        String dayMode = trimToNull(req.getDayMode()) == null
                ? LeaveDaysCalculator.MODE_WORKDAY : dayModeOr(req.getDayMode());
        BigDecimal days;
        if (cityPolicy != null) {
            // 城市政策类假期：天数与计算方式以政策为准，结束日期由开始日期自动带出
            dayMode = cityPolicy.dayMode();
            days = cityPolicy.days();
            startPeriod = LeaveDaysCalculator.PERIOD_AM;
            endPeriod = LeaveDaysCalculator.PERIOD_PM;
            startTime = null;
            endTime = null;
            end = daysCalculator.endDateFor(start, days, dayMode);
        } else {
            LeaveDaysCalculator.Result calc = daysCalculator.calculate(
                    start, startPeriod, startTime, end, endPeriod, endTime, dayMode);
            days = calc.days();
        }
        if (days.compareTo(nvl(rule.getMinDays())) < 0) {
            throw new BizException(EhrErrorCode.LEAVE_LEAVE_DAYS_INVALID,
                    "该请假类型最少请 " + rule.getMinDays() + " 天（当前 " + days + " 天）");
        }
        if (rule.getMaxDaysPerRequest() != null && days.compareTo(rule.getMaxDaysPerRequest()) > 0) {
            throw new BizException(EhrErrorCode.LEAVE_LEAVE_DAYS_INVALID,
                    "该请假类型单次最多 " + rule.getMaxDaysPerRequest() + " 天（当前 " + days + " 天）");
        }
        // 补请与提前申请限制
        LocalDate today = LocalDate.now();
        int allowPast = rule.getAllowPastDays() == null ? 0 : rule.getAllowPastDays();
        if (start.isBefore(today.minusDays(allowPast))) {
            throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID,
                    allowPast <= 0 ? "不允许补请历史日期" : "最多允许补请 " + allowPast + " 天内的日期");
        }
        int notice = rule.getMinNoticeDays() == null ? 0 : rule.getMinNoticeDays();
        if (notice > 0 && ChronoUnit.DAYS.between(today, start) < notice) {
            throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID,
                    "该请假类型需提前 " + notice + " 天申请");
        }
        // 附件：规则要求必传；不要求时带了也要校验归属与业务类型
        List<Attachment> attachments = resolveAttachments(req, rule, days, user.getAccountId());
        // 部门负责人请假必须指定审批转办人；类型规则也支持强制转办
        Long delegateAccountId = resolveDelegate(req.getDelegateAccountId(), emp, user,
                Integer.valueOf(1).equals(rule.getNeedDelegate()));
        String delegateName = "";
        if (delegateAccountId != null) {
            Employee delegate = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                    .eq(Employee::getAccountId, delegateAccountId).last("limit 1"));
            delegateName = nvl(delegate == null ? null : delegate.getName());
        }
        // 同一员工同一时间段不可重叠（按精确到半小时的起止时间判断）
        checkOverlap(user.getAccountId(), start, startPeriod, startTime, end, endPeriod, endTime);
        // 婚假、产假/陪产假等一次性假期：已有在途 / 已通过 / 已销假单据时不可再次申请
        if (Integer.valueOf(1).equals(rule.getOneTimeOnly())) {
            Long applied = leaveMapper.selectCount(new LambdaQueryWrapper<LeaveRequest>()
                    .eq(LeaveRequest::getAccountId, user.getAccountId())
                    .eq(LeaveRequest::getLeaveType, type)
                    .in(LeaveRequest::getStatus, 0, 1, 4));
            if (applied != null && applied > 0) {
                throw new BizException(EhrErrorCode.LEAVE_RULE_INVALID,
                        typeName(type) + "只能一次性休完，不可分段申请");
            }
        }

        LeaveRequest leave = new LeaveRequest();
        leave.setLeaveNo(bizNoService.next("ehr_leave"));
        leave.setEmployeeId(emp.getId());
        leave.setAccountId(user.getAccountId());
        leave.setApplicantName(emp.getName());
        leave.setLeaveType(type);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setStartPeriod(startPeriod);
        leave.setEndPeriod(endPeriod);
        leave.setStartTime(startTime);
        leave.setEndTime(endTime);
        leave.setDayMode(dayMode);
        leave.setDays(days);
        leave.setReason(req.getReason().trim());
        // 第一个附件回写 ehr_leave.attachment_id，兼容旧前端与旧数据
        leave.setAttachmentId(attachments.isEmpty() ? null : attachments.get(0).getId());
        leave.setDelegateAccountId(delegateAccountId);
        leave.setDelegateName(delegateName);
        leave.setStatus(0);
        // ① 本地写入独立提交（请假单 + 附件绑定 + 额度冻结同一事务，余额不足整单回滚）
        approvalLauncher.inTx(() -> {
            leaveMapper.insert(leave);
            for (Attachment a : attachments) {
                attachmentService.bind(a.getId(), "LEAVE", leave.getId());
                LeaveAttachment la = new LeaveAttachment();
                la.setLeaveId(leave.getId());
                la.setAttachmentId(a.getId());
                la.setFileName(a.getFileName() == null ? "" : a.getFileName());
                attachmentMapper.insert(la);
            }
            quotaService.freezeForLeave(leave, user.getAccountId());
        });

        // ②③ 远程发起 + 条件回写（发起确认失败时会把这张单自动撤销）
        approvalLauncher.launch(
                () -> buildLeaveStart(leave, emp, user),
                instanceId -> { /* 实例号已退役：在途判断统一查审批中心 */ },
                () -> {
                    int n = leaveMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                            .eq(LeaveRequest::getId, leave.getId())
                            .eq(LeaveRequest::getStatus, 0)
                            .set(LeaveRequest::getStatus, 3)
                            .set(LeaveRequest::getApproveNote, "发起审批失败，系统自动撤销"));
                    if (n > 0) {
                        attachmentMapper.delete(new LambdaQueryWrapper<LeaveAttachment>()
                                .eq(LeaveAttachment::getLeaveId, leave.getId()));
                    }
                });

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
        form.put("startTime", nvl(leave.getStartTime()));
        form.put("endTime", nvl(leave.getEndTime()));
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
        // 条件更新兜住重复投递：只有仍在待审批时才能落终态，后到的回调直接忽略
        int updated = leaveMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                .eq(LeaveRequest::getId, leaveId)
                .eq(LeaveRequest::getStatus, 0)
                .set(LeaveRequest::getStatus, st)
                .set(LeaveRequest::getApproveNote, req.getResultName() == null ? "" : req.getResultName())
                .set(LeaveRequest::getApprovedAt, LocalDateTime.now())
                .set(LeaveRequest::getApprovalInstanceId, null));
        if (updated == 0) {
            log.info("请假审批回调已处理过，忽略 leaveId={} 目标状态={}", leaveId, st);
            return;
        }
        if (st == 1) {
            // 通过：冻结额度转已用；负责人请假按起止时间登记审批委托
            quotaService.consumeForLeave(leave, null);
            registerLeaveDelegate(leave);
        } else {
            // 驳回 / 撤回 / 终止：释放提交时冻结的额度
            quotaService.releaseForLeave(leave, null);
            if (st == 3) {
                // 审批中心侧撤回 / 终止：证明材料关联行同步删除
                attachmentMapper.delete(new LambdaQueryWrapper<LeaveAttachment>()
                        .eq(LeaveAttachment::getLeaveId, leaveId));
            }
        }
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
        leaveMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                .eq(LeaveRequest::getId, id)
                .eq(LeaveRequest::getStatus, 0)
                .set(LeaveRequest::getStatus, 3)
                .set(LeaveRequest::getApprovalInstanceId, null));
        // 撤回后释放冻结额度（若回调已释放，幂等流水会跳过）
        quotaService.releaseForLeave(leave, user.getAccountId());
        // 请假单已撤销：证明材料关联行一并删除（附件本身留档）
        attachmentMapper.delete(new LambdaQueryWrapper<LeaveAttachment>()
                .eq(LeaveAttachment::getLeaveId, id));
        return toVO(leave, user);
    }

    /**
     * 解析证明材料：新旧字段合并去重；附件必须属于当前账号且业务类型为 LEAVE / TEMP。
     * 是否强制上传由「attachment_required=1 且请假小时数 ≥ proof_min_hours」决定。
     */
    private List<Attachment> resolveAttachments(LeaveApplyRequest req, LeaveTypeRule rule,
                                                BigDecimal days, Long accountId) {
        Set<Long> ids = new LinkedHashSet<>();
        if (req.getAttachmentIds() != null) {
            for (Long id : req.getAttachmentIds()) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty() && req.getAttachmentId() != null) {
            ids.add(req.getAttachmentId());
        }
        List<Attachment> attachments = new ArrayList<>();
        for (Long id : ids) {
            Attachment a = attachmentService.requireOwned(id, accountId);
            if (!"LEAVE".equalsIgnoreCase(a.getBizType()) && !"TEMP".equalsIgnoreCase(a.getBizType())) {
                throw new BizException(EhrErrorCode.ATTACHMENT_NOT_FOUND,
                        "附件类型不合法，仅支持请假证明或临时附件");
            }
            attachments.add(a);
        }
        BigDecimal minHours = rule.getProofMinHours() == null ? BigDecimal.ZERO : rule.getProofMinHours();
        BigDecimal hours = days.multiply(DEFAULT_WORK_HOURS);
        if (Integer.valueOf(1).equals(rule.getAttachmentRequired())
                && hours.compareTo(minHours) >= 0 && attachments.isEmpty()) {
            throw new BizException(EhrErrorCode.LEAVE_ATTACHMENT_REQUIRED, "该请假类型需上传证明材料");
        }
        return attachments;
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

    /** 部门负责人请假必填转办人；类型规则 forceRequired=true 时普通员工也必须填 */
    private Long resolveDelegate(Long raw, Employee applicant, SecurityUser user, boolean forceRequired) {
        if (raw == null) {
            if (forceRequired) {
                throw new BizException(ErrorCode.PARAM_ERROR, "该请假类型要求指定审批转办人");
            }
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

    /** 当前登录人对应的员工档案ID（额度/团队视图用） */
    public Long myEmployeeId() {
        SecurityUser user = currentUser();
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        if (me == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "当前账号未关联员工档案");
        }
        return me.getId();
    }

    /** 我的请假时间冲突：提交前给前端提示已占用的时段 */
    public List<LeaveVO> conflicts(String startRaw, String endRaw) {
        SecurityUser user = currentUser();
        LocalDate start = parseDate(startRaw);
        LocalDate end = parseDate(endRaw);
        return leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getAccountId, user.getAccountId())
                        .in(LeaveRequest::getStatus, 0, 1)
                        .le(LeaveRequest::getStartDate, end)
                        .ge(LeaveRequest::getEndDate, start))
                .stream().map(r -> toVO(r, user, false)).toList();
    }

    /**
     * 团队请假视图：按组织子树 + 日期区间查询待审批 / 已通过记录。
     * 有审批权或超管可查任意组织；普通主管只能查自己组织范围内。
     */
    public List<LeaveVO> team(Long orgId, String fromRaw, String toRaw) {
        SecurityUser user = currentUser();
        boolean canAll = user.isSuperAdmin() || user.getPermissions().contains(PermissionCodes.LEAVE_APPROVE);
        if (!canAll && !user.getPermissions().contains(PermissionCodes.LEAVE_VIEW_TEAM)) {
            throw new BizException(ErrorCode.FORBIDDEN, "没有查看团队请假的权限");
        }
        Employee me = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        Long root = orgId != null ? orgId : (me == null ? null : me.getOrgId());
        if (root == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "缺少组织参数，且当前账号未关联组织");
        }
        List<Long> orgIds = subtreeOrgIds(root);
        if (!canAll) {
            List<Long> mine = subtreeOrgIds(me.getOrgId());
            orgIds = orgIds.stream().filter(mine::contains).toList();
            if (orgIds.isEmpty()) {
                throw new BizException(ErrorCode.FORBIDDEN, "只能查看自己组织范围内的团队请假");
            }
        }
        List<Long> empIds = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                        .in(Employee::getOrgId, orgIds))
                .stream().map(Employee::getId).toList();
        if (empIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<LeaveRequest> w = new LambdaQueryWrapper<LeaveRequest>()
                .in(LeaveRequest::getEmployeeId, empIds)
                .in(LeaveRequest::getStatus, 0, 1)
                .orderByAsc(LeaveRequest::getStartDate);
        if (fromRaw != null && !fromRaw.isBlank()) {
            w.ge(LeaveRequest::getEndDate, parseDate(fromRaw));
        }
        if (toRaw != null && !toRaw.isBlank()) {
            w.le(LeaveRequest::getStartDate, parseDate(toRaw));
        }
        return leaveMapper.selectList(w).stream().map(r -> toVO(r, user, false)).toList();
    }

    /**
     * 提前返岗销假：按实际返岗日期重算天数，退回多请的额度，状态置 4。
     * 只允许对「已通过」的单据操作一次；本人或 HR 可操作。
     */
    @Transactional(rollbackFor = Exception.class)
    public LeaveVO earlyReturn(Long id, EarlyReturnRequest req) {
        SecurityUser user = currentUser();
        LeaveRequest leave = require(id);
        boolean owner = user.getAccountId().equals(leave.getAccountId());
        boolean hr = user.isSuperAdmin()
                || user.getPermissions().contains(PermissionCodes.LEAVE_QUOTA_ADJUST);
        if (!owner && !hr) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能为自己的请假办理销假");
        }
        if (leave.getStatus() == null || leave.getStatus() != 1) {
            throw new BizException(EhrErrorCode.LEAVE_EARLY_RETURN_INVALID,
                    Integer.valueOf(4).equals(leave.getStatus())
                            ? "该请假单已销假，不可重复销假"
                            : "只有已通过的请假单可以销假，且不可重复销假");
        }
        LocalDate returnDate = parseDate(req.getReturnDate());
        if (returnDate.isBefore(leave.getStartDate()) || returnDate.isAfter(leave.getEndDate())) {
            throw new BizException(EhrErrorCode.LEAVE_EARLY_RETURN_INVALID, "返岗日期需在请假起止日期内");
        }
        LocalDate actualEnd = returnDate.minusDays(1);
        BigDecimal actual;
        if (leave.getStartDate().isAfter(actualEnd)) {
            actual = BigDecimal.ZERO;
        } else {
            actual = daysCalculator.calculate(
                    leave.getStartDate(),
                    periodOr(leave.getStartPeriod(), LeaveDaysCalculator.PERIOD_AM),
                    leave.getStartTime(),
                    actualEnd, LeaveDaysCalculator.PERIOD_PM, null,
                    dayModeOr(leave.getDayMode())).days();
        }
        BigDecimal refund = nvl(leave.getDays()).subtract(actual);
        int updated = leaveMapper.update(null, new LambdaUpdateWrapper<LeaveRequest>()
                .eq(LeaveRequest::getId, id)
                .eq(LeaveRequest::getStatus, 1)
                .set(LeaveRequest::getStatus, 4)
                .set(LeaveRequest::getReturnDate, returnDate)
                .set(LeaveRequest::getDays, actual)
                .set(LeaveRequest::getApproveNote,
                        req.getNote() == null || req.getNote().isBlank() ? "提前返岗销假" : req.getNote()));
        if (updated == 0) {
            throw new BizException(EhrErrorCode.LEAVE_EARLY_RETURN_INVALID, "请假单状态已变化，请刷新后重试");
        }
        quotaService.returnDays(leave, refund, user.getAccountId());
        log.info("请假销假完成 leaveId={} 原{}天 实际{}天 退回{}天", id,
                leave.getDays(), actual, refund);
        return toVO(leaveMapper.selectById(id), user);
    }

    /** 组织子树ID：自身 + path 中包含该节点ID的所有后代（path 形如 1/2/3） */
    private List<Long> subtreeOrgIds(Long rootId) {
        List<Long> ids = new ArrayList<>();
        String r = String.valueOf(rootId);
        for (OrgNode n : orgMapper.selectList(new LambdaQueryWrapper<OrgNode>())) {
            if (n.getId().equals(rootId)) {
                ids.add(n.getId());
                continue;
            }
            String p = n.getPath();
            if (p == null) continue;
            if (p.startsWith(r + "/") || p.contains("/" + r + "/") || p.endsWith("/" + r)) {
                ids.add(n.getId());
            }
        }
        return ids;
    }

    /** 同一员工同一时段不可重叠：按精确到半小时的起止时间比较，首尾相接不算重叠 */
    private void checkOverlap(Long accountId, LocalDate start, String startPeriod, String startTime,
                              LocalDate end, String endPeriod, String endTime) {
        List<LeaveRequest> exist = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getAccountId, accountId)
                .in(LeaveRequest::getStatus, 0, 1)
                .le(LeaveRequest::getStartDate, end)
                .ge(LeaveRequest::getEndDate, start));
        LocalDateTime newFrom = startAt(start, startPeriod, startTime);
        LocalDateTime newTo = endAt(end, endPeriod, endTime);
        for (LeaveRequest e : exist) {
            LocalDateTime existFrom = startAt(e.getStartDate(),
                    periodOr(e.getStartPeriod(), LeaveDaysCalculator.PERIOD_AM), e.getStartTime());
            LocalDateTime existTo = endAt(e.getEndDate(),
                    periodOr(e.getEndPeriod(), LeaveDaysCalculator.PERIOD_PM), e.getEndTime());
            if (newFrom.isBefore(existTo) && existFrom.isBefore(newTo)) {
                throw new BizException(EhrErrorCode.LEAVE_DATE_OVERLAP,
                        "与已有请假单（" + e.getStartDate() + " ~ " + e.getEndDate() + "）时间重叠");
            }
        }
    }

    /** 请假开始时刻：优先用 HH:mm，旧数据按半天兜底（AM=09:00，PM=13:00） */
    private LocalDateTime startAt(LocalDate date, String period, String time) {
        LocalTime t = parseTimeOrNull(time);
        if (t == null) {
            t = LeaveDaysCalculator.PERIOD_PM.equalsIgnoreCase(period)
                    ? LocalTime.of(13, 0) : LocalTime.of(9, 0);
        }
        return LocalDateTime.of(date, t);
    }

    /** 请假结束时刻：优先用 HH:mm，旧数据按半天兜底（AM=12:00，PM=18:00） */
    private LocalDateTime endAt(LocalDate date, String period, String time) {
        LocalTime t = parseTimeOrNull(time);
        if (t == null) {
            t = LeaveDaysCalculator.PERIOD_AM.equalsIgnoreCase(period)
                    ? LocalTime.of(12, 0) : LocalTime.of(18, 0);
        }
        return LocalDateTime.of(date, t);
    }

    private LocalTime parseTimeOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String v = raw.trim();
            return LocalTime.parse(v.length() == 4 ? "0" + v : v);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String trimToNull(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private String periodOr(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String p = raw.trim().toUpperCase();
        return LeaveDaysCalculator.PERIOD_AM.equals(p) || LeaveDaysCalculator.PERIOD_PM.equals(p) ? p : fallback;
    }

    private String dayModeOr(String raw) {
        return raw != null && LeaveDaysCalculator.MODE_WORKDAY.equalsIgnoreCase(raw.trim())
                ? LeaveDaysCalculator.MODE_WORKDAY : LeaveDaysCalculator.MODE_NATURAL;
    }

    private BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /* ---------------- 内部工具 ---------------- */

    private SecurityUser currentUser() {
        return SecurityUtils.currentUser().orElseThrow(() ->
                new BizException(ErrorCode.UNAUTHORIZED));
    }

    /** 当前登录人对应的员工档案 */
    private Employee currentEmployee() {
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, currentUser().getAccountId()).last("limit 1"));
        if (emp == null) {
            throw new BizException(ErrorCode.NOT_FOUND,
                    "当前账号未关联员工档案，无法提交请假；请联系 HR 在人员管理里建档");
        }
        return emp;
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

    /** 证明材料列表；旧数据只有 attachment_id 时兜底返回一条 */
    private List<LeaveAttachmentVO> loadAttachments(LeaveRequest r) {
        List<LeaveAttachmentVO> out = new ArrayList<>();
        for (LeaveAttachment a : attachmentMapper.selectList(new LambdaQueryWrapper<LeaveAttachment>()
                .eq(LeaveAttachment::getLeaveId, r.getId())
                .orderByAsc(LeaveAttachment::getId))) {
            LeaveAttachmentVO vo = new LeaveAttachmentVO();
            vo.setId(a.getId());
            vo.setAttachmentId(a.getAttachmentId());
            vo.setFileName(a.getFileName() == null || a.getFileName().isBlank()
                    ? attachmentService.nameOf(a.getAttachmentId()) : a.getFileName());
            out.add(vo);
        }
        if (out.isEmpty() && r.getAttachmentId() != null) {
            LeaveAttachmentVO vo = new LeaveAttachmentVO();
            vo.setAttachmentId(r.getAttachmentId());
            vo.setFileName(attachmentService.nameOf(r.getAttachmentId()));
            out.add(vo);
        }
        return out;
    }

    private LeaveVO toVO(LeaveRequest r, SecurityUser user) {
        return toVO(r, user, true);
    }

    private LeaveVO toVO(LeaveRequest r, SecurityUser user, boolean withAttachments) {
        LeaveVO vo = new LeaveVO();
        vo.setId(r.getId());
        vo.setEmployeeId(r.getEmployeeId());
        vo.setAccountId(r.getAccountId());
        vo.setApplicantName(r.getApplicantName());
        vo.setLeaveType(r.getLeaveType());
        vo.setLeaveTypeName(typeName(r.getLeaveType()));
        vo.setStartDate(r.getStartDate() == null ? null : r.getStartDate().toString());
        vo.setEndDate(r.getEndDate() == null ? null : r.getEndDate().toString());
        vo.setStartPeriod(r.getStartPeriod());
        vo.setEndPeriod(r.getEndPeriod());
        vo.setStartTime(r.getStartTime());
        vo.setEndTime(r.getEndTime());
        vo.setDayMode(r.getDayMode());
        vo.setDays(r.getDays());
        vo.setReason(r.getReason());
        vo.setReturnDate(r.getReturnDate() == null ? null : r.getReturnDate().toString());
        vo.setAttachmentId(r.getAttachmentId());
        vo.setAttachmentName(attachmentService.nameOf(r.getAttachmentId()));
        if (withAttachments) {
            vo.setAttachments(loadAttachments(r));
        }
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
