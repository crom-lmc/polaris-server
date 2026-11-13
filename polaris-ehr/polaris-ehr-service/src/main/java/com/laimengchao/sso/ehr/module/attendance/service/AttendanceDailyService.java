package com.laimengchao.sso.ehr.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.attendance.dto.AttendancePunchRequest;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceCorrection;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceDaily;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceMonth;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendancePunch;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRule;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRuleSegment;
import com.laimengchao.sso.ehr.module.attendance.entity.Overtime;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceCorrectionMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceDailyMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceMonthMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendancePunchMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.OvertimeMapper;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendanceDailyVO;
import com.laimengchao.sso.ehr.module.attendance.vo.AttendancePunchVO;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.LeaveRequest;
import com.laimengchao.sso.ehr.module.leave.mapper.LeaveMapper;
import com.laimengchao.sso.ehr.module.leave.mapper.WorkCalendarMapper;
import com.laimengchao.sso.ehr.module.leave.service.LeaveDaysCalculator;
import com.laimengchao.sso.ehr.module.approval.service.ApproverResolver;
import com.laimengchao.sso.ehr.module.reminder.mapper.ReminderMapper;
import com.laimengchao.sso.ehr.security.DataScopeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考勤日结果：按工作日历 + 打卡 + 请假 + 加班，逐员工逐日重算（幂等）。
 * <p>
 * 缺卡只记 MISSING_PUNCH，等待补卡审批，绝不直接记旷工；
 * 缺勤小时数按实际分钟 / 60 保留 2 位，不做半天/整天向上取整。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceDailyService {

    private static final Map<String, String> STATUS_NAMES = Map.of(
            "NORMAL", "正常",
            "LATE", "迟到",
            "EARLY", "早退",
            "LATE_EARLY", "迟到早退",
            "ABSENT", "旷工",
            "MISSING_PUNCH", "缺卡",
            "LEAVE", "请假",
            "REST", "休息",
            "HOLIDAY", "法定节假日"
    );
    /** 需要给本人与直属主管发提醒的异常状态 */
    private static final Set<String> EXCEPTION_STATUSES =
            Set.of("LATE", "EARLY", "LATE_EARLY", "MISSING_PUNCH", "ABSENT");

    private final AttendanceDailyMapper dailyMapper;
    private final AttendancePunchMapper punchMapper;
    private final AttendanceCorrectionMapper correctionMapper;
    private final AttendanceMonthMapper monthMapper;
    private final AttendanceRuleService ruleService;
    private final EmployeeMapper employeeMapper;
    private final LeaveMapper leaveMapper;
    private final WorkCalendarMapper calendarMapper;
    private final OvertimeMapper overtimeMapper;
    private final LeaveDaysCalculator daysCalculator;
    private final DataScopeService dataScopeService;
    private final AttendanceCalendarService calendarService;
    private final ReminderMapper reminderMapper;
    private final ApproverResolver approverResolver;
    private final ObjectMapper objectMapper;

    /** 生成指定日期的全员日结果，并刷新当月汇总。 */
    @Transactional(rollbackFor = Exception.class)
    public int generate(LocalDate date) {
        if (date == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择考勤日期");
        }
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode())
                .and(w -> w.isNull(Employee::getHireDate).or().le(Employee::getHireDate, date)));
        YearMonth ym = YearMonth.from(date);
        // 一次性预查本月已确认的员工集合，避免 monthConfirmed N+1
        Set<Long> confirmedEmpIds = monthMapper.selectList(new LambdaQueryWrapper<AttendanceMonth>()
                        .eq(AttendanceMonth::getMonth, ym.toString())
                        .eq(AttendanceMonth::getConfirmed, 1))
                .stream().map(AttendanceMonth::getEmployeeId).collect(Collectors.toSet());
        for (Employee emp : employees) {
            if (confirmedEmpIds.contains(emp.getId())) {
                continue;
            }
            generateForEmployee(emp, date);
        }
        for (Employee emp : employees) {
            if (confirmedEmpIds.contains(emp.getId())) {
                continue;
            }
            recalcMonth(emp, ym);
        }
        return employees.size();
    }

    /**
     * 请假状态变化（审批通过 / 撤销 / 销假）后，重算该员工指定日期区间内
     * <b>已生成</b>的日结果，并刷新对应月度汇总。
     * <p>
     * 只刷新已有的行，不给尚未生成（如未来日期）补行，避免凭空产生缺卡提醒；
     * 已确认锁定的月份按既有规则跳过。日结果是派生数据，调用方需自行兜底异常。
     */
    public void recalculate(Long employeeId, LocalDate from, LocalDate to) {
        if (employeeId == null || from == null || to == null || to.isBefore(from)) {
            return;
        }
        Employee emp = employeeMapper.selectById(employeeId);
        if (emp == null || !workingOn(emp, to)) {
            return;
        }
        List<AttendanceDaily> rows = dailyMapper.selectList(new LambdaQueryWrapper<AttendanceDaily>()
                .eq(AttendanceDaily::getEmployeeId, employeeId)
                .ge(AttendanceDaily::getWorkDate, from)
                .le(AttendanceDaily::getWorkDate, to)
                .orderByAsc(AttendanceDaily::getWorkDate));
        Set<YearMonth> months = new LinkedHashSet<>();
        for (AttendanceDaily row : rows) {
            YearMonth ym = YearMonth.from(row.getWorkDate());
            if (monthConfirmed(employeeId, ym)) {
                continue;
            }
            generateForEmployee(emp, row.getWorkDate());
            months.add(ym);
        }
        months.forEach(ym -> recalcMonth(emp, ym));
    }

    /** 与 {@link #generate(LocalDate)} 同一口径：仅试用期 / 在职，且已入职 */
    private boolean workingOn(Employee emp, LocalDate date) {
        EmployeeStatus status = EmployeeStatus.of(emp.getStatus());
        return status != null && status.isWorking()
                && (emp.getHireDate() == null || !emp.getHireDate().isAfter(date));
    }

    /* ---------- 查询 ---------- */

    public R.Page<AttendanceDailyVO> page(String month, Long employeeId, String status,
                                          int page, int size) {
        YearMonth ym = parseMonth(month);
        LambdaQueryWrapper<AttendanceDaily> w = new LambdaQueryWrapper<>();
        if (employeeId != null) {
            w.eq(AttendanceDaily::getEmployeeId, requireAccessible(employeeId).getId());
        } else {
            List<Long> ids = accessibleEmployees().stream().map(Employee::getId).toList();
            if (ids.isEmpty()) {
                return R.Page.of(List.of(), 0);
            }
            w.in(AttendanceDaily::getEmployeeId, ids);
        }
        if (status != null && !status.isBlank()) {
            w.eq(AttendanceDaily::getStatus, status.trim().toUpperCase());
        }
        w.ge(AttendanceDaily::getWorkDate, ym.atDay(1))
                .le(AttendanceDaily::getWorkDate, ym.atEndOfMonth())
                .orderByDesc(AttendanceDaily::getWorkDate)
                .orderByDesc(AttendanceDaily::getId);
        IPage<AttendanceDaily> ip = dailyMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> names = employeeNames(ip.getRecords().stream()
                .map(AttendanceDaily::getEmployeeId).collect(Collectors.toSet()));
        List<AttendanceDailyVO> rows = ip.getRecords().stream()
                .map(d -> toVO(d, names.getOrDefault(d.getEmployeeId(), "")))
                .toList();
        return R.Page.of(rows, ip.getTotal());
    }

    /** 当前登录员工本人某月的日结果。 */
    public List<AttendanceDailyVO> my(String month) {
        Employee emp = currentEmployee();
        YearMonth ym = parseMonth(month);
        List<AttendanceDaily> rows = dailyMapper.selectList(new LambdaQueryWrapper<AttendanceDaily>()
                .eq(AttendanceDaily::getEmployeeId, emp.getId())
                .ge(AttendanceDaily::getWorkDate, ym.atDay(1))
                .le(AttendanceDaily::getWorkDate, ym.atEndOfMonth())
                .orderByAsc(AttendanceDaily::getWorkDate));
        return rows.stream().map(d -> toVO(d, nvl(emp.getName()))).toList();
    }

    public R.Page<AttendancePunchVO> punches(Long employeeId, LocalDate date, int page, int size) {
        LambdaQueryWrapper<AttendancePunch> w = new LambdaQueryWrapper<>();
        if (employeeId != null) {
            w.eq(AttendancePunch::getEmployeeId, requireAccessible(employeeId).getId());
        } else {
            List<Long> ids = accessibleEmployees().stream().map(Employee::getId).toList();
            if (ids.isEmpty()) {
                return R.Page.of(List.of(), 0);
            }
            w.in(AttendancePunch::getEmployeeId, ids);
        }
        if (date != null) {
            w.ge(AttendancePunch::getPunchTime, date.atStartOfDay())
                    .lt(AttendancePunch::getPunchTime, date.plusDays(1).atStartOfDay());
        }
        w.orderByDesc(AttendancePunch::getPunchTime).orderByDesc(AttendancePunch::getId);
        IPage<AttendancePunch> ip = punchMapper.selectPage(new Page<>(page, size), w);
        // 打卡表只存员工 ID，翻页后按本页 ID 批量回填姓名与工号，避免逐行查询
        Map<Long, Employee> emps = employees(ip.getRecords().stream()
                .map(AttendancePunch::getEmployeeId).collect(Collectors.toSet()));
        List<AttendancePunchVO> rows = ip.getRecords().stream()
                .map(p -> toPunchVO(p, emps.get(p.getEmployeeId())))
                .toList();
        return R.Page.of(rows, ip.getTotal());
    }

    /** HR 手工补录打卡：落 source=MANUAL，然后重算当天。 */
    @Transactional(rollbackFor = Exception.class)
    public AttendancePunch addPunch(AttendancePunchRequest req) {
        if (req == null || req.getEmployeeId() == null || req.getPunchTime() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "员工和打卡时间不能为空");
        }
        Employee emp = employeeMapper.selectById(req.getEmployeeId());
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "员工不存在");
        }
        AttendancePunch punch = new AttendancePunch();
        punch.setId(IdWorker.getId());
        punch.setEmployeeId(emp.getId());
        punch.setPunchTime(req.getPunchTime());
        punch.setPunchType(normalizePunchType(req.getPunchType()));
        punch.setSource("MANUAL");
        punch.setDeviceNo("");
        punch.setLocation("");
        punch.setRemark(req.getRemark() == null ? "" : req.getRemark());
        punchMapper.insert(punch);
        generate(req.getPunchTime().toLocalDate());
        return punch;
    }

    /* ---------- 日结果计算 ---------- */

    private AttendanceDaily generateForEmployee(Employee emp, LocalDate date) {
        String dayType = calendarService.resolveDayType(date);
        boolean workday = "WORKDAY".equals(dayType);
        AttendanceRuleService.ResolvedRule resolved = ruleService.resolveRule(emp);
        AttendanceRule rule = resolved == null ? null : resolved.rule();
        List<AttendanceRuleSegment> segments = resolved == null ? List.of() : resolved.segments();
        boolean needPunch = rule != null && Integer.valueOf(1).equals(rule.getRequirePunch());
        int standardMinutes = rule == null || rule.getWorkHours() == null
                ? 8 * 60
                : rule.getWorkHours().multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).intValue();

        List<AttendancePunch> punches = punchMapper.selectList(new LambdaQueryWrapper<AttendancePunch>()
                .eq(AttendancePunch::getEmployeeId, emp.getId())
                .ge(AttendancePunch::getPunchTime, date.atStartOfDay())
                .lt(AttendancePunch::getPunchTime, date.plusDays(1).atStartOfDay())
                .orderByAsc(AttendancePunch::getPunchTime));

        AttendancePunch inPunch = null;
        AttendancePunch outPunch = null;
        for (AttendancePunch p : punches) {
            String type = p.getPunchType();
            if (type == null || "IN".equalsIgnoreCase(type)) {
                if (inPunch == null) {
                    inPunch = p;
                }
            }
            if (type == null || "OUT".equalsIgnoreCase(type)) {
                outPunch = p;
            }
        }
        // 只有一条自动判定卡时，视为只打了上班卡，下班卡缺失
        if (punches.size() == 1 && punches.get(0).getPunchType() == null) {
            outPunch = null;
        }
        LocalDateTime firstIn = inPunch == null ? null : inPunch.getPunchTime();
        LocalDateTime lastOut = outPunch == null ? null : outPunch.getPunchTime();

        // 只统计「已通过（status=1）」的请假单；已销假(4) / 已撤销(3) / 已驳回(2) 均不再影响考勤
        List<LeaveRequest> leaves = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getEmployeeId, emp.getId())
                .eq(LeaveRequest::getStatus, 1)
                .le(LeaveRequest::getStartDate, date)
                .ge(LeaveRequest::getEndDate, date));
        LeaveRequest fullDayLeave = null;
        for (LeaveRequest lv : leaves) {
            if (coversFullDay(lv, date)) {
                fullDayLeave = lv;
                break;
            }
        }
        int leaveMinutes = leaveMinutesOn(leaves, date, standardMinutes);

        int missingCards = (firstIn == null ? 1 : 0) + (lastOut == null ? 1 : 0);
        boolean missing = workday && needPunch && fullDayLeave == null && missingCards > 0;

        int lateMinutes = 0;
        int earlyMinutes = 0;
        LocalDateTime shiftStart = null;
        LocalDateTime shiftEnd = null;
        if (rule != null && workday && fullDayLeave == null) {
            shiftStart = LocalDateTime.of(date, rule.getWorkStart());
            shiftEnd = LocalDateTime.of(date, rule.getWorkEnd());
            if (Integer.valueOf(1).equals(rule.getCrossDay())
                    || rule.getWorkEnd().isBefore(rule.getWorkStart())) {
                shiftEnd = shiftEnd.plusDays(1);
            }
            int lateGrace = rule.getLateGrace() == null ? 0 : rule.getLateGrace();
            int earlyGrace = rule.getEarlyGrace() == null ? 0 : rule.getEarlyGrace();
            if (firstIn != null) {
                lateMinutes = (int) Math.max(0, Duration.between(shiftStart, firstIn).toMinutes() - lateGrace);
            }
            if (lastOut != null) {
                earlyMinutes = (int) Math.max(0, Duration.between(lastOut, shiftEnd).toMinutes() - earlyGrace);
            }
        }

        int workMinutes = 0;
        if (firstIn != null && lastOut != null && lastOut.isAfter(firstIn)) {
            long minutes = Duration.between(firstIn, lastOut).toMinutes();
            if (rule != null) {
                minutes -= restOverlapMinutes(firstIn, lastOut, date, rule);
            }
            workMinutes = (int) Math.max(0, minutes);
        }
        if (workday) {
            workMinutes = Math.max(0, workMinutes - leaveMinutes);
        }

        int absentMinutes = 0;
        if (workday) {
            absentMinutes = lateMinutes + earlyMinutes;
            if (missing) {
                absentMinutes += missingCards * (standardMinutes / 2);
            }
            int threshold = rule == null || rule.getAbsentMinMinutes() == null ? 0 : rule.getAbsentMinMinutes();
            if (threshold > 0 && absentMinutes < threshold) {
                absentMinutes = 0;
            }
        }

        String status;
        if (!workday) {
            status = "LEGAL_HOLIDAY".equals(dayType) ? "HOLIDAY" : "REST";
        } else if (fullDayLeave != null) {
            status = "LEAVE";
        } else if (missing) {
            status = "MISSING_PUNCH";
        } else if (lateMinutes > 0 && earlyMinutes > 0) {
            status = "LATE_EARLY";
        } else if (lateMinutes > 0) {
            status = "LATE";
        } else if (earlyMinutes > 0) {
            status = "EARLY";
        } else {
            status = "NORMAL";
        }

        int overtimeMinutes = overtimeMapper.selectList(new LambdaQueryWrapper<Overtime>()
                        .eq(Overtime::getEmployeeId, emp.getId())
                        .eq(Overtime::getWorkDate, date)
                        .eq(Overtime::getStatus, 1))
                .stream().map(Overtime::getHours).filter(h -> h != null)
                .map(h -> h.multiply(new BigDecimal(60)).setScale(0, RoundingMode.HALF_UP).intValue())
                .reduce(0, Integer::sum);

        AttendanceDaily row = new AttendanceDaily();
        row.setEmployeeId(emp.getId());
        row.setWorkDate(date);
        row.setRuleId(rule == null ? null : rule.getId());
        row.setRuleSnapshot(snapshot(rule, segments));
        row.setShiftStart(rule == null ? null : rule.getWorkStart());
        row.setShiftEnd(rule == null ? null : rule.getWorkEnd());
        row.setFirstIn(firstIn);
        row.setLastOut(lastOut);
        row.setLateMinutes(lateMinutes);
        row.setEarlyMinutes(earlyMinutes);
        row.setAbsentMinutes(absentMinutes);
        row.setAbsentHours(BigDecimal.valueOf(absentMinutes)
                .divide(new BigDecimal(60), 2, RoundingMode.HALF_UP));
        row.setWorkMinutes(workMinutes);
        row.setOvertimeMinutes(overtimeMinutes);
        row.setDayType(dayType);
        row.setStatus(status);
        row.setLeaveId(fullDayLeave == null ? null : fullDayLeave.getId());
        row.setLeaveType(fullDayLeave == null ? null : fullDayLeave.getLeaveType());
        row.setCorrectionStatus(correctionStatus(emp.getId(), date));
        row.setRemark("");
        saveDaily(row);
        remindIfException(emp, row);
        return row;
    }

    private void saveDaily(AttendanceDaily row) {
        AttendanceDaily existing = dailyMapper.selectOne(new LambdaQueryWrapper<AttendanceDaily>()
                .eq(AttendanceDaily::getEmployeeId, row.getEmployeeId())
                .eq(AttendanceDaily::getWorkDate, row.getWorkDate())
                .last("limit 1"));
        if (existing == null) {
            row.setId(IdWorker.getId());
            dailyMapper.insert(row);
        } else {
            row.setId(existing.getId());
            dailyMapper.updateById(row);
        }
    }

    /* ---------- 月度汇总 ---------- */

    public void recalcMonth(Employee emp, YearMonth ym) {
        AttendanceMonth existing = monthMapper.selectOne(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getEmployeeId, emp.getId())
                .eq(AttendanceMonth::getMonth, ym.toString())
                .last("limit 1"));
        if (existing != null && Integer.valueOf(1).equals(existing.getConfirmed())) {
            return;
        }
        LocalDate first = ym.atDay(1);
        LocalDate last = ym.atEndOfMonth();
        LocalDate today = LocalDate.now();
        // 仅统计到今天：未来日期还没发生，不算实际出勤也不算缺勤
        LocalDate actualLast = last.isBefore(today) ? last : today;

        Set<LocalDate> holidays = Set.copyOf(calendarMapper.holidays(first, last));
        Set<LocalDate> extras = Set.copyOf(calendarMapper.extraWorkdays(first, last));

        // 整月应出勤天数（页面展示用）
        BigDecimal shouldDays = BigDecimal.ZERO;
        // 已过工作日（截至今天），用于 actualDays 计算
        BigDecimal pastShouldDays = BigDecimal.ZERO;
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean workday = extras.contains(d) || (!holidays.contains(d) && !weekend);
            if (workday) {
                shouldDays = shouldDays.add(BigDecimal.ONE);
                if (!d.isAfter(actualLast)) {
                    pastShouldDays = pastShouldDays.add(BigDecimal.ONE);
                }
            }
        }

        // Bug 1 修复：只统计已通过（status=1）的请假单；销假(4)/撤销(3)/驳回(2) 均不再计入
        BigDecimal leaveDays = BigDecimal.ZERO;
        List<LeaveRequest> leaves = leaveMapper.selectList(new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getEmployeeId, emp.getId())
                .eq(LeaveRequest::getStatus, 1)
                .lt(LeaveRequest::getStartDate, last.plusDays(1))
                .ge(LeaveRequest::getEndDate, first));
        for (LeaveRequest lv : leaves) {
            // 请假区间截断到今天（actualLast），未来部分不计入已过请假天数
            LocalDate leaveEnd = lv.getEndDate();
            if (leaveEnd.isBefore(first)) {
                continue;
            }
            LocalDate s = lv.getStartDate().isBefore(first) ? first : lv.getStartDate();
            LocalDate e = leaveEnd.isAfter(actualLast) ? actualLast : leaveEnd;
            if (e.isBefore(s)) {
                continue;
            }
            String sp = s.equals(lv.getStartDate()) && lv.getStartPeriod() != null
                    ? lv.getStartPeriod() : "AM";
            String ep = e.equals(lv.getEndDate()) && lv.getEndPeriod() != null
                    ? lv.getEndPeriod() : "PM";
            String st = s.equals(lv.getStartDate()) ? lv.getStartTime() : null;
            String et = e.equals(lv.getEndDate()) ? lv.getEndTime() : null;
            try {
                leaveDays = leaveDays.add(daysCalculator.calculate(s, sp, st, e, ep, et,
                        lv.getDayMode() == null ? "NATURAL" : lv.getDayMode()).days());
            } catch (BizException ex) {
                log.warn("月度请假天数计算失败 leaveId={}", lv.getId(), ex);
            }
        }

        List<AttendanceDaily> rows = dailyMapper.selectList(new LambdaQueryWrapper<AttendanceDaily>()
                .eq(AttendanceDaily::getEmployeeId, emp.getId())
                .ge(AttendanceDaily::getWorkDate, first)
                .le(AttendanceDaily::getWorkDate, last));
        int lateMinutes = 0;
        int earlyMinutes = 0;
        BigDecimal absentHours = BigDecimal.ZERO;
        int workMinutes = 0;
        int overtimeMinutes = 0;
        Set<LocalDate> coveredWorkdays = new java.util.HashSet<>();
        for (AttendanceDaily d : rows) {
            lateMinutes += nvl(d.getLateMinutes());
            earlyMinutes += nvl(d.getEarlyMinutes());
            absentHours = absentHours.add(d.getAbsentHours() == null ? BigDecimal.ZERO : d.getAbsentHours());
            workMinutes += nvl(d.getWorkMinutes());
            overtimeMinutes += nvl(d.getOvertimeMinutes());
            if ("WORKDAY".equals(d.getDayType())) {
                coveredWorkdays.add(d.getWorkDate());
            }
        }
        absentHours = absentHours.setScale(2, RoundingMode.HALF_UP);
        BigDecimal absentDays = absentHours.divide(new BigDecimal("8"), 2, RoundingMode.HALF_UP);

        // Bug 2 修复：过去工作日但没有 daily 行的，整天视为缺勤
        // （mock 只跑了前两周，后两周没 punch 也没 daily，不能被算成满勤）
        for (LocalDate d = first; !d.isAfter(actualLast); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            boolean workday = extras.contains(d) || (!holidays.contains(d) && !weekend);
            if (workday && !coveredWorkdays.contains(d)) {
                absentDays = absentDays.add(BigDecimal.ONE);
            }
        }
        absentDays = absentDays.setScale(2, RoundingMode.HALF_UP);

        // 实际出勤 = 已过工作日 - 已过请假 - 缺勤（含未生成日结果的过去工作日）
        BigDecimal actualDays = pastShouldDays.subtract(leaveDays).subtract(absentDays)
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        AttendanceMonth row = new AttendanceMonth();
        row.setEmployeeId(emp.getId());
        row.setMonth(ym.toString());
        row.setShouldDays(shouldDays.setScale(2, RoundingMode.HALF_UP));
        row.setActualDays(actualDays);
        row.setLeaveDays(leaveDays.setScale(2, RoundingMode.HALF_UP));
        row.setOvertimeHours(BigDecimal.valueOf(overtimeMinutes)
                .divide(new BigDecimal(60), 2, RoundingMode.HALF_UP));
        row.setAbsentDays(absentDays);
        row.setLateMinutes(lateMinutes);
        row.setEarlyMinutes(earlyMinutes);
        row.setAbsentHours(absentHours);
        row.setWorkHours(BigDecimal.valueOf(workMinutes)
                .divide(new BigDecimal(60), 2, RoundingMode.HALF_UP));
        row.setConfirmed(0);
        if (existing == null) {
            row.setId(IdWorker.getId());
            monthMapper.insert(row);
        } else {
            row.setId(existing.getId());
            monthMapper.updateById(row);
        }
    }

    /* ---------- 工具 ---------- */

    private boolean monthConfirmed(Long employeeId, YearMonth ym) {
        return monthMapper.selectCount(new LambdaQueryWrapper<AttendanceMonth>()
                .eq(AttendanceMonth::getEmployeeId, employeeId)
                .eq(AttendanceMonth::getMonth, ym.toString())
                .eq(AttendanceMonth::getConfirmed, 1)) > 0;
    }

    /** 迟到/早退/缺卡/旷工：给本人与直属主管各发一条提醒（同一员工同一天只发一次）。 */
    private void remindIfException(Employee emp, AttendanceDaily row) {
        if (!EXCEPTION_STATUSES.contains(row.getStatus()) || row.getWorkDate() == null) {
            return;
        }
        String detail = exceptionText(row);
        String title = "考勤异常提醒 · " + row.getWorkDate();
        String content = nvl(emp.getName()) + " " + row.getWorkDate() + " " + detail + "，请及时处理（补卡或确认）。";
        Set<Long> receivers = new LinkedHashSet<>();
        if (emp.getAccountId() != null) {
            receivers.add(emp.getAccountId());
        }
        ApproverResolver.Approver leader = approverResolver.resolve(emp, emp.getAccountId());
        if (leader != null && leader.exists()) {
            receivers.add(leader.accountId());
        }
        for (Long receiverId : receivers) {
            reminderMapper.insertIgnore(IdWorker.getId(), emp.getId(), receiverId,
                    "ATTENDANCE_EXCEPTION", row.getId(), row.getWorkDate(), title, content);
        }
    }

    private String exceptionText(AttendanceDaily row) {
        StringBuilder sb = new StringBuilder("考勤异常：")
                .append(STATUS_NAMES.getOrDefault(row.getStatus(), row.getStatus()));
        if (nvl(row.getLateMinutes()) > 0) {
            sb.append("，迟到 ").append(row.getLateMinutes()).append(" 分钟");
        }
        if (nvl(row.getEarlyMinutes()) > 0) {
            sb.append("，早退 ").append(row.getEarlyMinutes()).append(" 分钟");
        }
        return sb.toString();
    }

    private String snapshot(AttendanceRule rule, List<AttendanceRuleSegment> segments) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("rule", rule);
            map.put("segments", segments);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("考勤规则快照序列化失败 ruleId={}", rule == null ? null : rule.getId(), e);
            return null;
        }
    }

    private long restOverlapMinutes(LocalDateTime firstIn, LocalDateTime lastOut,
                                    LocalDate date, AttendanceRule rule) {
        if (rule.getRestStart() == null || rule.getRestEnd() == null) {
            return 0;
        }
        LocalDateTime restStart = LocalDateTime.of(date, rule.getRestStart());
        LocalDateTime restEnd = LocalDateTime.of(date, rule.getRestEnd());
        if (!restEnd.isAfter(restStart)) {
            restEnd = restEnd.plusDays(1);
        }
        LocalDateTime start = firstIn.isAfter(restStart) ? firstIn : restStart;
        LocalDateTime end = lastOut.isBefore(restEnd) ? lastOut : restEnd;
        return Math.max(0, Duration.between(start, end).toMinutes());
    }

    /**
     * 请假对考勤生效的结束日期：已销假（status=4）且填了返岗日期的，返岗当天起不再算请假，
     * 否则仍以原结束日期为准。
     */
    private LocalDate effectiveLeaveEnd(LeaveRequest lv) {
        if (Integer.valueOf(4).equals(lv.getStatus()) && lv.getReturnDate() != null
                && lv.getReturnDate().isBefore(lv.getEndDate())) {
            return lv.getReturnDate().minusDays(1);
        }
        return lv.getEndDate();
    }

    private boolean coversFullDay(LeaveRequest lv, LocalDate date) {
        LocalDate end = effectiveLeaveEnd(lv);
        if (date.isBefore(lv.getStartDate()) || date.isAfter(end)) {
            return false;
        }
        String sp = lv.getStartPeriod() == null ? "AM" : lv.getStartPeriod();
        String ep = end.equals(lv.getEndDate()) && lv.getEndPeriod() != null ? lv.getEndPeriod() : "PM";
        if (date.equals(lv.getStartDate()) && date.equals(end)) {
            return "AM".equals(sp) && "PM".equals(ep);
        }
        if (date.equals(lv.getStartDate())) {
            return "AM".equals(sp);
        }
        if (date.equals(end)) {
            return "PM".equals(ep);
        }
        return true;
    }

    private int leaveMinutesOn(List<LeaveRequest> leaves, LocalDate date, int standardMinutes) {
        BigDecimal days = BigDecimal.ZERO;
        for (LeaveRequest lv : leaves) {
            LocalDate end = effectiveLeaveEnd(lv);
            if (date.isBefore(lv.getStartDate()) || date.isAfter(end)) {
                continue;
            }
            LocalDate s = lv.getStartDate().isBefore(date) ? date : lv.getStartDate();
            LocalDate e = end.isAfter(date) ? date : end;
            String sp = s.equals(lv.getStartDate()) && lv.getStartPeriod() != null
                    ? lv.getStartPeriod() : "AM";
            String ep = e.equals(lv.getEndDate()) && lv.getEndPeriod() != null
                    ? lv.getEndPeriod() : "PM";
            String st = s.equals(lv.getStartDate()) ? lv.getStartTime() : null;
            String et = e.equals(lv.getEndDate()) ? lv.getEndTime() : null;
            try {
                days = days.add(daysCalculator.calculate(s, sp, st, e, ep, et,
                        lv.getDayMode() == null ? "NATURAL" : lv.getDayMode()).days());
            } catch (BizException ex) {
                log.warn("单日请假折算失败 leaveId={} date={}", lv.getId(), date, ex);
            }
        }
        return days.multiply(BigDecimal.valueOf(standardMinutes))
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private int correctionStatus(Long employeeId, LocalDate date) {
        List<AttendanceCorrection> rows = correctionMapper.selectList(
                new LambdaQueryWrapper<AttendanceCorrection>()
                        .eq(AttendanceCorrection::getEmployeeId, employeeId)
                        .eq(AttendanceCorrection::getWorkDate, date));
        int state = 0;
        for (AttendanceCorrection c : rows) {
            if (Integer.valueOf(1).equals(c.getStatus())) {
                return 2;
            }
            if (Integer.valueOf(0).equals(c.getStatus())) {
                state = 1;
            }
        }
        return state;
    }

    private String normalizePunchType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String t = type.trim().toUpperCase();
        if (!"IN".equals(t) && !"OUT".equals(t)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "打卡类型只能是 IN 或 OUT");
        }
        return t;
    }

    private Employee currentEmployee() {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow();
        Employee emp = employeeMapper.selectOne(new LambdaQueryWrapper<Employee>()
                .eq(Employee::getAccountId, user.getAccountId()).last("limit 1"));
        if (emp == null) {
            throw new BizException(EhrErrorCode.EMP_NOT_FOUND, "当前账号未绑定员工档案");
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
        w.orderByAsc(Employee::getEmpNo);
        return employeeMapper.selectList(w);
    }

    private Map<Long, String> employeeNames(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (Employee e : employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getId, ids))) {
            map.put(e.getId(), nvl(e.getName()));
        }
        return map;
    }

    private Map<Long, Employee> employees(Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Employee> map = new HashMap<>();
        for (Employee e : employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getId, ids))) {
            map.put(e.getId(), e);
        }
        return map;
    }

    private AttendancePunchVO toPunchVO(AttendancePunch p, Employee emp) {
        AttendancePunchVO vo = new AttendancePunchVO();
        vo.setId(p.getId());
        vo.setEmployeeId(p.getEmployeeId());
        vo.setEmployeeName(emp == null ? "" : nvl(emp.getName()));
        vo.setEmployeeNo(emp == null ? "" : nvl(emp.getEmpNo()));
        vo.setPunchTime(p.getPunchTime());
        vo.setPunchType(p.getPunchType());
        vo.setSource(p.getSource());
        vo.setDeviceNo(nvl(p.getDeviceNo()));
        vo.setLocation(nvl(p.getLocation()));
        vo.setRemark(nvl(p.getRemark()));
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }

    private AttendanceDailyVO toVO(AttendanceDaily d, String employeeName) {
        AttendanceDailyVO vo = new AttendanceDailyVO();
        vo.setId(d.getId());
        vo.setEmployeeId(d.getEmployeeId());
        vo.setEmployeeName(employeeName);
        vo.setWorkDate(d.getWorkDate());
        vo.setShiftStart(d.getShiftStart());
        vo.setShiftEnd(d.getShiftEnd());
        vo.setFirstIn(d.getFirstIn());
        vo.setLastOut(d.getLastOut());
        vo.setLateMinutes(nvl(d.getLateMinutes()));
        vo.setEarlyMinutes(nvl(d.getEarlyMinutes()));
        vo.setAbsentMinutes(nvl(d.getAbsentMinutes()));
        vo.setAbsentHours(d.getAbsentHours() == null ? BigDecimal.ZERO : d.getAbsentHours());
        vo.setWorkMinutes(nvl(d.getWorkMinutes()));
        vo.setOvertimeMinutes(nvl(d.getOvertimeMinutes()));
        vo.setDayType(d.getDayType());
        vo.setStatus(d.getStatus());
        vo.setStatusName(STATUS_NAMES.getOrDefault(d.getStatus(), d.getStatus()));
        vo.setLeaveId(d.getLeaveId());
        vo.setLeaveType(d.getLeaveType());
        vo.setCorrectionStatus(nvl(d.getCorrectionStatus()));
        vo.setRemark(nvl(d.getRemark()));
        return vo;
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "月份格式应为 yyyy-MM");
        }
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
