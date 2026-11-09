package com.laimengchao.sso.ehr.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.approval.support.ApprovalLauncher;
import com.laimengchao.sso.ehr.module.attendance.dto.OvertimeRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceMonth;
import com.laimengchao.sso.ehr.module.attendance.entity.Overtime;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceMonthMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.OvertimeMapper;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceMonthVO;
import com.laimengchao.sso.ehr.module.attendance.vo.OvertimeVO;
import com.laimengchao.sso.ehr.module.employee.dto.ApprovalCallbackRequest;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveQuota;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.WorkCalendarMapper;
import com.laimengchao.sso.ehr.module.leave.service.LeaveDaysCalculator;
import com.laimengchao.sso.ehr.module.leave.service.LeaveQuotaService;
import com.laimengchao.sso.ehr.security.DataScopeService;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.settings.client.api.ApprovalApi;
import com.laimengchao.sso.settings.client.dto.ApprovalActionDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalInstanceDTO;
import com.laimengchao.sso.settings.client.dto.ApprovalStartDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 轻量考勤（P1-2）：加班申请走审批中心，通过后按小时折算调休额度；
 * 月度汇总以工作日历 + 已通过请假/加班实时计算，HR 确认后锁定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final String BIZ_OVERTIME = "ehr.overtime";
    /** 月累计加班超 8 小时或节假日加班：主管通过后再加一级 HR 终审 */
    public static final String BIZ_OVERTIME_HR = "ehr.overtime.hr";
    private static final String CALLBACK_PATH = "/internal/ehr/approval-callback";

    private static final Map<String, String> TYPE_NAMES = Map.of(
            "NORMAL", "工作日加班",
            "HOLIDAY", "节假日加班",
            "RESTDAY", "休息日加班"
    );
    private static final Map<Integer, String> STATUS_NAMES = Map.of(
            0, "待审批",
            1, "已通过",
            2, "已驳回",
            3, "已撤销"
    );

    private final OvertimeMapper overtimeMapper;
    private final AttendanceMonthMapper monthMapper;
    private final EmployeeMapper employeeMapper;
    private final LeaveMapper leaveMapper;
    private final WorkCalendarMapper calendarMapper;
    private final LeaveDaysCalculator daysCalculator;
    private final LeaveQuotaService quotaService;
    private final DataScopeService dataScopeService;
    private final ApprovalLauncher approvalLauncher;
    private final ApprovalApi approvalApi;

    @Value("${ehr.self-base-url:http://127.0.0.1:9006}")
    private String selfBaseUrl;

    /** 1 小时加班折 1 小时调休；额度按天记，折算分母可配 */
    @Value("${ehr.attendance.overtime-hours-per-day:8}")
    private BigDecimal hoursPerDay;

    /* ---------- 加班申请 ---------- */

    public OvertimeVO submit(OvertimeRequest req) {
        Employee emp = currentEmployee();
        if (req.getWorkDate() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择加班日期");
        }
        if (req.getWorkDate().isAfter(LocalDate.now())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能为未来日期提交加班");
        }
        if (req.getHours() == null || req.getHours().compareTo(BigDecimal.ZERO) <= 0
                || req.getHours().compareTo(new BigDecimal("24")) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加班时长需在 0-24 小时之间");
        }
        String type = req.getType() == null ? "NORMAL" : req.getType().toUpperCase();
        if (!TYPE_NAMES.containsKey(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加班类型不合法");
        }
        SecurityUser user = currentUser();
        Overtime o = new Overtime();
        o.setId(IdWorker.getId());
        o.setEmployeeId(emp.getId());
        o.setAccountId(user.getAccountId());
        o.setWorkDate(req.getWorkDate());
        o.setHours(req.getHours().setScale(2, RoundingMode.HALF_UP));
        o.setType(type);
        o.setReason(req.getReason() == null ? "" : req.getReason());
        o.setStatus(0);
        o.setCreatedAt(LocalDateTime.now());
        overtimeMapper.insert(o);

        BigDecimal monthTotal = approvedMonthHours(emp, o.getWorkDate()).add(o.getHours());
        String bizType = monthTotal.compareTo(new BigDecimal("8")) > 0 || "HOLIDAY".equals(type)
                ? BIZ_OVERTIME_HR : BIZ_OVERTIME;
        approvalLauncher.launch(
                () -> buildStart(o, emp, user, bizType, monthTotal),
                instanceId -> { /* 实例号已退役：在途统一查审批中心 */ },
                () -> overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                        .eq(Overtime::getId, o.getId())
                        .eq(Overtime::getStatus, 0)
                        .set(Overtime::getStatus, 3)
                        .set(Overtime::getApproveNote, "发起审批失败，系统自动撤销")));
        Overtime fresh = overtimeMapper.selectById(o.getId());
        return toVO(fresh == null ? o : fresh, user, true);
    }

    public void cancel(Long id) {
        SecurityUser user = currentUser();
        Overtime o = overtimeMapper.selectById(id);
        if (o == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "加班申请不存在");
        }
        if (!user.getAccountId().equals(o.getAccountId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能撤销自己的加班申请");
        }
        if (o.getStatus() == null || o.getStatus() != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "只有待审批的加班可以撤销");
        }
        Long runningId = approvalLauncher.latestRunningId(BIZ_OVERTIME, String.valueOf(id));
        if (runningId != null) {
            ApprovalActionDTO act = new ApprovalActionDTO();
            act.setOperatorId(user.getAccountId());
            act.setOperatorName(user.getUsername());
            act.setNote("申请人撤销加班");
            try {
                com.laimengchao.sso.settings.client.dto.R<ApprovalInstanceDTO> resp =
                        approvalApi.withdraw(runningId, act);
                if (resp == null || !resp.ok()) {
                    throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID,
                            resp == null || resp.getMessage() == null ? "撤回审批单失败" : resp.getMessage());
                }
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("撤回加班审批单失败 instanceId={}", runningId, e);
                throw new BizException(EhrErrorCode.APPROVAL_STATE_INVALID, "撤回审批单失败，请稍后重试");
            }
        }
        overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                .eq(Overtime::getId, id)
                .eq(Overtime::getStatus, 0)
                .set(Overtime::getStatus, 3));
    }

    /** 我的 / 团队（全部）列表；数据范围始终生效，避免越权 */
    public R.Page<OvertimeVO> list(String scope, Long employeeId, String month, int page, int size) {
        SecurityUser user = currentUser();
        LambdaQueryWrapper<Overtime> w = new LambdaQueryWrapper<>();
        boolean mine = employeeId == null && "mine".equalsIgnoreCase(scope == null ? "mine" : scope);
        if (employeeId != null) {
            Employee emp = requireAccessible(employeeId);
            w.eq(Overtime::getEmployeeId, emp.getId());
        } else if (mine) {
            w.eq(Overtime::getAccountId, user.getAccountId());
        } else {
            List<Long> ids = accessibleEmployees().stream().map(Employee::getId).toList();
            if (ids.isEmpty()) {
                return R.Page.of(List.of(), 0);
            }
            w.in(Overtime::getEmployeeId, ids);
        }
        if (month != null && !month.isBlank()) {
            YearMonth ym = parseMonth(month);
            w.ge(Overtime::getWorkDate, ym.atDay(1)).le(Overtime::getWorkDate, ym.atEndOfMonth());
        }
        w.orderByDesc(Overtime::getWorkDate).orderByDesc(Overtime::getId);
        IPage<Overtime> ip = overtimeMapper.selectPage(new Page<>(page, size), w);
        List<Overtime> records = ip.getRecords();
        Map<Long, String> names = employeeNames(records.stream().map(Overtime::getEmployeeId).collect(Collectors.toSet()));
        List<OvertimeVO> vos = records.stream()
                .map(o -> {
                    OvertimeVO vo = toVO(o, user, false);
                    vo.setEmployeeName(names.getOrDefault(o.getEmployeeId(), ""));
                    return vo;
                }).toList();
        return R.Page.of(vos, ip.getTotal());
    }

    /* ---------- 审批回调 ---------- */

    public void handleApprovalResult(ApprovalCallbackRequest req) {
        Long id = parseLong(req.getBizId());
        if (id == null) {
            return;
        }
        Overtime o = overtimeMapper.selectById(id);
        if (o == null || o.getStatus() == null || o.getStatus() != 0) {
            log.info("加班回调重复投递或单据不存在，忽略 id={}", id);
            return;
        }
        if (Boolean.TRUE.equals(req.getApproved())) {
            int updated = overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                    .eq(Overtime::getId, id)
                    .eq(Overtime::getStatus, 0)
                    .set(Overtime::getStatus, 1));
            if (updated == 0) {
                return;
            }
            addCompensatory(o);
        } else {
            int target = req.getResult() != null && (req.getResult() == 3 || req.getResult() == 4) ? 3 : 2;
            overtimeMapper.update(null, new LambdaUpdateWrapper<Overtime>()
                    .eq(Overtime::getId, id)
                    .eq(Overtime::getStatus, 0)
                    .set(Overtime::getStatus, target));
        }
    }

    /** 加班转调休：1 小时加班 = 1 小时调休，按 8 小时/天折算入 COMPENSATORY 额度；流水 bizKey 保证只加一次 */
    private void addCompensatory(Overtime o) {
        if (o.getWorkDate() == null || o.getHours() == null) {
            return;
        }
        int year = o.getWorkDate().getYear();
        LeaveQuota q = quotaService.getOrCreate(o.getEmployeeId(), "COMPENSATORY", year);
        if (q == null) {
            return;
        }
        BigDecimal divisor = hoursPerDay == null || hoursPerDay.compareTo(BigDecimal.ZERO) <= 0
                ? new BigDecimal("8") : hoursPerDay;
        BigDecimal days = o.getHours().divide(divisor, 4, RoundingMode.HALF_UP);
        quotaService.adjust(q.getId(), days, null,
                "加班转调休 " + o.getWorkDate() + " " + o.getHours() + " 小时",
                "OVERTIME:" + o.getId());
    }

    /* ---------- 月度汇总 ---------- */

    public List<AttendanceMonthVO> monthly(String month, Long employeeId) {
        YearMonth ym = parseMonth(month);
        if (employeeId != null) {
            return List.of(computeOne(requireAccessible(employeeId), ym));
        }
        return accessibleEmployees().stream().map(emp -> computeOne(emp, ym)).toList();
    }

    public AttendanceMonthVO confirm(String month, Long employeeId) {
        YearMonth ym = parseMonth(month);
        Employee emp = requireAccessible(employeeId);
        AttendanceMonth row = monthMapper.selectOne(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getEmployeeId, emp.getId())
                .eq(AttendanceMonth::getMonth, ym.toString())
                .last("limit 1"));
        if (row != null && Integer.valueOf(1).equals(row.getConfirmed())) {
            return toVO(row, emp.getName());
        }
        AttendanceMonth computed = computeRaw(emp, ym);
        computed.setConfirmed(1);
        save(computed, row);
        return toVO(computed, emp.getName());
    }

    private AttendanceMonthVO computeOne(Employee emp, YearMonth ym) {
        AttendanceMonth row = monthMapper.selectOne(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getEmployeeId, emp.getId())
                .eq(AttendanceMonth::getMonth, ym.toString())
                .last("limit 1"));
        if (row != null && Integer.valueOf(1).equals(row.getConfirmed())) {
            return toVO(row, emp.getName());
        }
        AttendanceMonth computed = computeRaw(emp, ym);
        save(computed, row);
        computed.setConfirmed(0);
        return toVO(computed, emp.getName());
    }

    private AttendanceMonth computeRaw(Employee emp, YearMonth ym) {
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        Set<LocalDate> holidays = Set.copyOf(calendarMapper.holidays(first, last));
        Set<LocalDate> extras = Set.copyOf(calendarMapper.extraWorkdays(first, last));
        BigDecimal should = BigDecimal.ZERO;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean workday = extras.contains(d) || (!holidays.contains(d) && !weekend);
            if (workday) {
                should = should.add(BigDecimal.ONE);
            }
        }

        BigDecimal leaveDays = BigDecimal.ZERO;
        List<LeaveRequest> leaves = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getEmployeeId, emp.getId())
                .in(LeaveRequest::getStatus, 1, 4)
                .lt(LeaveRequest::getStartDate, last.plusDays(1))
                .ge(LeaveRequest::getEndDate, first));
        for (LeaveRequest lv : leaves) {
            LocalDate s = lv.getStartDate().isBefore(first) ? first : lv.getStartDate();
            LocalDate e = lv.getEndDate().isAfter(last) ? last : lv.getEndDate();
            String sp = "AM";
            String ep = "PM";
            if (s.equals(lv.getStartDate())) {
                sp = lv.getStartPeriod() == null ? "AM" : lv.getStartPeriod();
            }
            if (e.equals(lv.getEndDate())) {
                ep = lv.getEndPeriod() == null ? "PM" : lv.getEndPeriod();
            }
            String mode = lv.getDayMode() == null ? "NATURAL" : lv.getDayMode();
            LeaveDaysCalculator.Result r = daysCalculator.calculate(s, sp, e, ep, mode);
            leaveDays = leaveDays.add(r.days());
        }

        BigDecimal overtimeHours = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                        .eq(Overtime::getEmployeeId, emp.getId())
                        .eq(Overtime::getStatus, 1)
                        .ge(Overtime::getWorkDate, first)
                        .le(Overtime::getWorkDate, last))
                .stream().map(Overtime::getHours).filter(h -> h != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal actual = should.subtract(leaveDays).max(BigDecimal.ZERO);
        AttendanceMonth row = new AttendanceMonth();
        row.setId(IdWorker.getId());
        row.setEmployeeId(emp.getId());
        row.setMonth(ym.toString());
        row.setShouldDays(should.setScale(2, RoundingMode.HALF_UP));
        row.setActualDays(actual.setScale(2, RoundingMode.HALF_UP));
        row.setLeaveDays(leaveDays.setScale(2, RoundingMode.HALF_UP));
        row.setOvertimeHours(overtimeHours.setScale(2, RoundingMode.HALF_UP));
        row.setAbsentDays(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        row.setConfirmed(0);
        return row;
    }

    private void save(AttendanceMonth computed, AttendanceMonth existing) {
        if (existing == null) {
            computed.setCreatedAt(LocalDateTime.now());
            monthMapper.insert(computed);
        } else {
            computed.setId(existing.getId());
            monthMapper.updateById(computed);
        }
    }

    /* ---------- 工具 ---------- */

    private ApprovalStartDTO buildStart(Overtime o, Employee emp, SecurityUser user,
                                        String bizType, BigDecimal monthHours) {
        ApprovalStartDTO dto = new ApprovalStartDTO();
        dto.setBizType(bizType);
        dto.setBizId(String.valueOf(o.getId()));
        dto.setBizNo(String.valueOf(o.getId()));
        dto.setTitle("加班审批 · " + nvl(emp.getName()) + " · " + o.getWorkDate());
        dto.setApplicantId(user.getAccountId());
        dto.setApplicantName(user.getDisplayName());
        dto.setApplicantOrgId(emp.getOrgId());
        dto.setCallbackUrl(selfBaseUrl + CALLBACK_PATH);

        Map<String, Object> form = new LinkedHashMap<>();
        form.put("employeeId", String.valueOf(emp.getId()));
        form.put("empNo", nvl(emp.getEmpNo()));
        form.put("name", nvl(emp.getName()));
        form.put("workDate", o.getWorkDate() == null ? null : o.getWorkDate().toString());
        form.put("hours", o.getHours());
        form.put("type", o.getType());
        form.put("typeName", TYPE_NAMES.getOrDefault(o.getType(), o.getType()));
        form.put("monthlyHours", monthHours);
        form.put("reason", nvl(o.getReason()));
        form.put("submitter", nvl(user.getDisplayName()));
        dto.setFormData(form);
        return dto;
    }

    /** 当月已通过加班小时数（不含本次） */
    private BigDecimal approvedMonthHours(Employee emp, LocalDate date) {
        if (date == null) {
            return BigDecimal.ZERO;
        }
        return overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                        .eq(Overtime::getEmployeeId, emp.getId())
                        .eq(Overtime::getStatus, 1)
                        .ge(Overtime::getWorkDate, date.withDayOfMonth(1))
                        .le(Overtime::getWorkDate, date.withDayOfMonth(date.lengthOfMonth())))
                .stream().map(Overtime::getHours).filter(h -> h != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Employee currentEmployee() {
        SecurityUser user = currentUser();
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId())
                .last("limit 1"));
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "当前账号未绑定员工档案，无法提交加班");
        }
        return emp;
    }

    private Employee requireAccessible(Long employeeId) {
        Employee emp = employeeId == null ? null : employeeMapper.selectById(employeeId);
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工不存在");
        }
        if (!dataScopeService.canAccess(emp)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该员工的考勤");
        }
        return emp;
    }

    private List<Employee> accessibleEmployees() {
        LambdaQueryWrapper<Employee> w = new LambdaQueryWrapper<>();
        dataScopeService.applyTo(w);
        w.in(Employee::getStatus, 30, 40).orderByAsc(Employee::getEmpNo);
        return employeeMapper.selectList(w);
    }

    private Map<Long, String> employeeNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeMapper.selectList(new LambdaQueryWrapper<Employee>().in(Employee::getId, ids))
                .stream().collect(Collectors.toMap(Employee::getId, e -> nvl(e.getName()), (a, b) -> a));
    }

    private OvertimeVO toVO(Overtime o, SecurityUser user, boolean forceMine) {
        OvertimeVO vo = new OvertimeVO();
        vo.setId(o.getId());
        vo.setEmployeeId(o.getEmployeeId());
        Employee emp = employeeMapper.selectById(o.getEmployeeId());
        vo.setEmployeeName(emp == null ? "" : nvl(emp.getName()));
        vo.setWorkDate(o.getWorkDate());
        vo.setHours(o.getHours());
        vo.setType(o.getType());
        vo.setTypeName(TYPE_NAMES.getOrDefault(o.getType(), o.getType()));
        vo.setReason(nvl(o.getReason()));
        vo.setStatus(o.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(o.getStatus(), String.valueOf(o.getStatus())));
        vo.setApproveNote(nvl(o.getApproveNote()));
        vo.setCreatedAt(o.getCreatedAt());
        vo.setMine(forceMine || (o.getAccountId() != null && o.getAccountId().equals(user.getAccountId())));
        return vo;
    }

    private AttendanceMonthVO toVO(AttendanceMonth m, String name) {
        AttendanceMonthVO vo = new AttendanceMonthVO();
        vo.setId(m.getId());
        vo.setEmployeeId(m.getEmployeeId());
        vo.setEmployeeName(name);
        vo.setMonth(m.getMonth());
        vo.setShouldDays(m.getShouldDays());
        vo.setActualDays(m.getActualDays());
        vo.setLeaveDays(m.getLeaveDays());
        vo.setOvertimeHours(m.getOvertimeHours());
        vo.setAbsentDays(m.getAbsentDays());
        vo.setConfirmed(m.getConfirmed());
        return vo;
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
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
