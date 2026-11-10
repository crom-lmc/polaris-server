package com.laimengchao.sso.ehr.module.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.ehr.common.BizNoService;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceDaily;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceMockLog;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendancePunch;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRule;
import com.laimengchao.sso.ehr.module.attendance.entity.Overtime;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceDailyMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendanceMockLogMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.AttendancePunchMapper;
import com.laimengchao.sso.ehr.module.attendance.mapper.OvertimeMapper;
import com.laimengchao.sso.ehr.module.employee.constant.EmployeeStatus;
import com.laimengchao.sso.ehr.module.employee.entity.Employee;
import com.laimengchao.sso.ehr.module.employee.mapper.EmployeeMapper;
import com.laimengchao.sso.ehr.module.leave.entity.WorkCalendar;
import com.laimengchao.sso.ehr.module.leave.mapper.WorkCalendarMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 模拟考勤：每天 01:00 生成 T-1 的打卡流水与加班单，便于演示看板/缺勤/补卡。
 * <p>
 * 只在工作日跑；mock_log.mock_date 唯一，重复触发直接跳过。
 * 缺卡只生成一条打卡，不直接记旷工，等待补卡流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceMockService {

    private final AttendanceMockLogMapper mockLogMapper;
    private final AttendancePunchMapper punchMapper;
    private final AttendanceDailyMapper dailyMapper;
    private final OvertimeMapper overtimeMapper;
    private final EmployeeMapper employeeMapper;
    private final WorkCalendarMapper calendarMapper;
    private final AttendanceRuleService ruleService;
    private final AttendanceDailyService dailyService;
    private final AttendanceService attendanceService;
    private final BizNoService bizNoService;

    @Value("${ehr.attendance.mock.enabled:false}")
    private boolean enabled;

    /**
     * 生成指定日期的模拟考勤；受 ehr.attendance.mock.enabled 开关控制，
     * 关闭时抛出明确提示，避免生产环境误生成数据。
     *
     * @return 生成的打卡条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int run(LocalDate date) {
        if (!enabled) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "模拟考勤已关闭（ehr.attendance.mock.enabled=false），生产环境请勿开启");
        }
        LocalDate mockDate = date == null ? LocalDate.now().minusDays(1) : date;
        if (!isWorkday(mockDate)) {
            log.info("模拟考勤跳过非工作日 date={}", mockDate);
            return 0;
        }
        AttendanceMockLog exists = mockLogMapper.selectOne(new LambdaQueryWrapper<AttendanceMockLog>()
                .eq(AttendanceMockLog::getMockDate, mockDate).last("limit 1"));
        if (exists != null) {
            log.info("模拟考勤已生成过，跳过 date={}", mockDate);
            return 0;
        }
        List<Employee> employees = employeeMapper.selectList(new LambdaQueryWrapper<Employee>()
                .in(Employee::getStatus, EmployeeStatus.PROBATION.getCode(), EmployeeStatus.ACTIVE.getCode())
                .and(w -> w.isNull(Employee::getHireDate).or().le(Employee::getHireDate, mockDate)));
        if (employees.isEmpty()) {
            writeLog(mockDate, 0, 0, 0, "无在职员工");
            return 0;
        }
        Collections.shuffle(employees, ThreadLocalRandom.current());

        Map<Long, Integer> lateCount = monthlyLateCount(mockDate);
        List<Employee> absent = new ArrayList<>();
        for (Employee emp : employees) {
            if (absent.size() >= 3) {
                break;
            }
            if (lateCount.getOrDefault(emp.getId(), 0) < 3) {
                absent.add(emp);
            }
        }
        Set<Long> absentIds = absent.stream().map(Employee::getId).collect(Collectors.toSet());

        List<Employee> rest = employees.stream()
                .filter(e -> !absentIds.contains(e.getId())).collect(Collectors.toList());
        Collections.shuffle(rest, ThreadLocalRandom.current());
        List<Employee> overtimeEmployees = rest.subList(0, Math.min(10, rest.size()));

        // 2% 概率制造一条孤立缺卡（只有上班卡），用于演示补卡
        Employee missingDemo = null;
        if (!rest.isEmpty() && ThreadLocalRandom.current().nextDouble() < 0.02) {
            missingDemo = rest.get(ThreadLocalRandom.current().nextInt(rest.size()));
        }

        int punchCount = 0;
        List<String> details = new ArrayList<>();
        for (Employee emp : employees) {
            AttendanceRuleService.ResolvedRule resolved = ruleService.resolveRule(emp);
            AttendanceRule rule = resolved == null ? null : resolved.rule();
            LocalTime start = rule == null || rule.getWorkStart() == null
                    ? LocalTime.of(9, 0) : rule.getWorkStart();
            LocalTime end = rule == null || rule.getWorkEnd() == null
                    ? LocalTime.of(18, 0) : rule.getWorkEnd();

            if (absentIds.contains(emp.getId())) {
                boolean late = ThreadLocalRandom.current().nextBoolean();
                int offset = 1 + ThreadLocalRandom.current().nextInt(60);
                LocalDateTime in;
                LocalDateTime out;
                if (late) {
                    in = LocalDateTime.of(mockDate, start).plusMinutes(offset);
                    out = LocalDateTime.of(mockDate, end);
                } else {
                    in = LocalDateTime.of(mockDate, start);
                    out = LocalDateTime.of(mockDate, end).minusMinutes(offset);
                }
                insertPunch(emp.getId(), in, "IN");
                insertPunch(emp.getId(), out, "OUT");
                punchCount += 2;
                details.add(emp.getName() + (late ? "迟到" : "早退") + offset + "分钟");
            } else {
                int before = ThreadLocalRandom.current().nextInt(16);
                int after = ThreadLocalRandom.current().nextInt(16);
                insertPunch(emp.getId(), LocalDateTime.of(mockDate, start).minusMinutes(before), "IN");
                punchCount++;
                if (missingDemo == null || !missingDemo.getId().equals(emp.getId())) {
                    insertPunch(emp.getId(), LocalDateTime.of(mockDate, end).plusMinutes(after), "OUT");
                    punchCount++;
                } else {
                    details.add(emp.getName() + "缺下班卡");
                }
            }
        }

        for (Employee emp : overtimeEmployees) {
            BigDecimal hours = BigDecimal.valueOf(2 + ThreadLocalRandom.current().nextInt(7) * 0.5)
                    .setScale(2, RoundingMode.HALF_UP);
            Overtime o = new Overtime();
            o.setId(IdWorker.getId());
            o.setOvertimeNo(bizNoService.next("ehr_overtime"));
            o.setEmployeeId(emp.getId());
            o.setAccountId(emp.getAccountId());
            o.setWorkDate(mockDate);
            o.setHours(hours);
            o.setType("NORMAL");
            o.setReason("模拟考勤：自动生成加班");
            o.setStatus(1);
            o.setSettleStatus(1);
            o.setEffectiveHours(hours);
            o.setSettledAt(LocalDateTime.now());
            o.setCalcDetail("1:1 转调休");
            o.setApproveNote("");
            o.setCreatedAt(LocalDateTime.now());
            overtimeMapper.insert(o);
            attendanceService.grantCompensatory(o, hours);
            details.add(emp.getName() + "加班" + hours + "小时");
        }

        String detail = String.join("；", details);
        if (detail.length() > 1000) {
            detail = detail.substring(0, 1000);
        }
        writeLog(mockDate, punchCount, absent.size(), overtimeEmployees.size(), detail);
        dailyService.generate(mockDate);
        return punchCount;
    }

    private void insertPunch(Long employeeId, LocalDateTime time, String type) {
        AttendancePunch punch = new AttendancePunch();
        punch.setId(IdWorker.getId());
        punch.setEmployeeId(employeeId);
        punch.setPunchTime(time);
        punch.setPunchType(type);
        punch.setSource("MOCK");
        punch.setDeviceNo("MOCK-01");
        punch.setLocation("模拟考勤");
        punch.setRemark("模拟考勤自动生成");
        punchMapper.insert(punch);
    }

    private void writeLog(LocalDate date, int punchCount, int absentCount, int overtimeCount, String detail) {
        AttendanceMockLog row = new AttendanceMockLog();
        row.setId(IdWorker.getId());
        row.setMockDate(date);
        row.setPunchCount(punchCount);
        row.setAbsentCount(absentCount);
        row.setOvertimeCount(overtimeCount);
        row.setDetail(detail == null ? "" : detail);
        mockLogMapper.insert(row);
    }

    /** 当月已产生的迟到/早退天数，用于「每人每月最多 3 次」限制。 */
    private Map<Long, Integer> monthlyLateCount(LocalDate date) {
        YearMonth ym = YearMonth.from(date);
        List<AttendanceDaily> rows = dailyMapper.selectList(new LambdaQueryWrapper<AttendanceDaily>()
                .in(AttendanceDaily::getStatus, "LATE", "EARLY", "LATE_EARLY")
                .ge(AttendanceDaily::getWorkDate, ym.atDay(1))
                .le(AttendanceDaily::getWorkDate, ym.atEndOfMonth()));
        Map<Long, Integer> map = new HashMap<>();
        for (AttendanceDaily d : rows) {
            map.merge(d.getEmployeeId(), 1, Integer::sum);
        }
        return map;
    }

    private boolean isWorkday(LocalDate date) {
        WorkCalendar cal = calendarMapper.selectById(date);
        if (cal != null) {
            return Integer.valueOf(1).equals(cal.getIsWorkday());
        }
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
