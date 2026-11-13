package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.leave.mapper.WorkCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 请假天数计算：支持精确到半小时的起止时间与工作日模式，额度冻结 / 销假重算共用。
 * <p>
 * 工作时长口径与默认考勤规则一致：09:00-12:00、13:00-18:00，全天 8 小时（480 分钟），
 * 请假时长不足整天时按实际落在工作时段内的分钟数折算：天数 = 分钟数 / 480，保留 2 位小数。
 * <p>
 * 工作日判定：优先读 ehr_work_calendar；未配置的日期按「周六周日休息」兜底，
 * 已配置的节假日（is_workday=0）和调休上班日（is_workday=1）优先于周末规则。
 */
@Component
@RequiredArgsConstructor
public class LeaveDaysCalculator {

    public static final String PERIOD_AM = "AM";
    public static final String PERIOD_PM = "PM";
    public static final String MODE_NATURAL = "NATURAL";
    public static final String MODE_WORKDAY = "WORKDAY";

    public static final LocalTime WORK_START = LocalTime.of(9, 0);
    public static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    public static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    public static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final long WORK_MINUTES_PER_DAY = 480;

    private final WorkCalendarMapper calendarMapper;

    public record Result(BigDecimal days, String warning) {
    }

    /**
     * 兼容旧调用（只有上午/下午）：等价于未传精确时间。
     */
    public Result calculate(LocalDate start, String startPeriod, LocalDate end, String endPeriod, String dayMode) {
        return calculate(start, startPeriod, null, end, endPeriod, null, dayMode);
    }

    /**
     * @param startPeriod 开始半天：AM（上午） / PM（下午）；startTime 有值时以时间为准
     * @param startTime   开始时间 HH:mm，必须按半小时（00 / 30 分）选择
     * @param endPeriod   结束半天：AM / PM；endTime 有值时以时间为准
     * @param endTime     结束时间 HH:mm，必须按半小时（00 / 30 分）选择
     */
    public Result calculate(LocalDate start, String startPeriod, String startTime,
                            LocalDate end, String endPeriod, String endTime, String dayMode) {
        if (start == null || end == null) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "请假日期必填");
        }
        if (end.isBefore(start)) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "结束日期不能早于开始日期");
        }
        if (start.getYear() != end.getYear()) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "暂不支持跨年请假，请拆成两张单据");
        }
        LocalTime st = parseTime(startTime);
        LocalTime et = parseTime(endTime);
        if (st != null && et != null) {
            return calculateByTime(start, st, end, et, dayMode);
        }
        return calculateByPeriod(start, startPeriod, end, endPeriod, dayMode);
    }

    /** 精确到半小时的算法：按每天工作时段累计分钟数，再折算成天 */
    private Result calculateByTime(LocalDate start, LocalTime st, LocalDate end, LocalTime et, String dayMode) {
        LocalDateTime startAt = LocalDateTime.of(start, st);
        LocalDateTime endAt = LocalDateTime.of(end, et);
        if (!endAt.isAfter(startAt)) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "结束时间不能早于开始时间");
        }
        if (Duration.between(startAt, endAt).toMinutes() < 30) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "请假时长不能少于 30 分钟");
        }
        boolean workday = MODE_WORKDAY.equalsIgnoreCase(dayMode == null ? MODE_NATURAL : dayMode);
        WorkCalendar cal = workday ? loadCalendar(start, end) : null;
        long minutes = 0;
        for (LocalDate cur = start; !cur.isAfter(end); cur = cur.plusDays(1)) {
            if (cal != null && !cal.isWorkday(cur)) {
                continue;
            }
            minutes += workMinutes(cur, start, st, end, et);
        }
        BigDecimal days = BigDecimal.valueOf(minutes)
                .divide(BigDecimal.valueOf(WORK_MINUTES_PER_DAY), 3, RoundingMode.HALF_UP);
        return new Result(days, cal == null ? null : cal.warning);
    }

    /** 某一天与 [startAt, endAt] 相交的工作分钟数；午休不计入 */
    private long workMinutes(LocalDate date, LocalDate start, LocalTime st, LocalDate end, LocalTime et) {
        long total = 0;
        total += overlapMinutes(date, WORK_START, LUNCH_START, start, st, end, et);
        total += overlapMinutes(date, LUNCH_END, WORK_END, start, st, end, et);
        return total;
    }

    private long overlapMinutes(LocalDate date, LocalTime windowFrom, LocalTime windowTo,
                                LocalDate start, LocalTime st, LocalDate end, LocalTime et) {
        LocalDateTime from = LocalDateTime.of(date, windowFrom);
        LocalDateTime to = LocalDateTime.of(date, windowTo);
        if (date.equals(start)) {
            LocalDateTime s = LocalDateTime.of(start, st);
            if (s.isAfter(from)) {
                from = s;
            }
        }
        if (date.equals(end)) {
            LocalDateTime e = LocalDateTime.of(end, et);
            if (e.isBefore(to)) {
                to = e;
            }
        }
        return to.isAfter(from) ? Duration.between(from, to).toMinutes() : 0;
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            LocalTime t = LocalTime.parse(raw.trim().length() == 4 ? "0" + raw.trim() : raw.trim());
            if (t.getMinute() != 0 && t.getMinute() != 30) {
                throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "请假时间需按半小时选择（00 分或 30 分）");
            }
            return t;
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "请假时间格式不正确，应为 HH:mm");
        }
    }

    private Result calculateByPeriod(LocalDate start, String startPeriod, LocalDate end, String endPeriod, String dayMode) {
        if (start == null || end == null) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "请假日期必填");
        }
        if (end.isBefore(start)) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "结束日期不能早于开始日期");
        }
        if (start.getYear() != end.getYear()) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "暂不支持跨年请假，请拆成两张单据");
        }
        String sp = normalizePeriod(startPeriod, PERIOD_AM);
        String ep = normalizePeriod(endPeriod, PERIOD_PM);
        boolean workday = MODE_WORKDAY.equalsIgnoreCase(dayMode == null ? MODE_NATURAL : dayMode);

        if (start.equals(end)) {
            if (PERIOD_PM.equals(sp) && PERIOD_AM.equals(ep)) {
                throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID,
                        "同一天的结束时间不能早于开始时间（下午 → 上午不合法）");
            }
            BigDecimal d;
            if (PERIOD_AM.equals(sp) && PERIOD_AM.equals(ep)) {
                d = new BigDecimal("0.5");
            } else if (PERIOD_PM.equals(sp) && PERIOD_PM.equals(ep)) {
                d = new BigDecimal("0.5");
            } else {
                d = BigDecimal.ONE;
            }
            String warning = null;
            if (workday) {
                WorkCalendar cal = loadCalendar(start, end);
                if (!cal.isWorkday(start)) {
                    d = BigDecimal.ZERO;
                    warning = "所选日期不是工作日";
                }
                warning = cal.warning;
            }
            return new Result(d, warning);
        }

        if (!workday) {
            long mid = ChronoUnit.DAYS.between(start, end) - 1;
            BigDecimal d = BigDecimal.valueOf(mid)
                    .add(PERIOD_AM.equals(sp) ? BigDecimal.ONE : new BigDecimal("0.5"))
                    .add(PERIOD_PM.equals(ep) ? BigDecimal.ONE : new BigDecimal("0.5"));
            return new Result(d, null);
        }

        WorkCalendar cal = loadCalendar(start, end);
        BigDecimal d = BigDecimal.ZERO;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            BigDecimal fraction;
            if (cur.equals(start) && cur.equals(end)) {
                fraction = BigDecimal.ONE;
            } else if (cur.equals(start)) {
                fraction = PERIOD_AM.equals(sp) ? BigDecimal.ONE : new BigDecimal("0.5");
            } else if (cur.equals(end)) {
                fraction = PERIOD_PM.equals(ep) ? BigDecimal.ONE : new BigDecimal("0.5");
            } else {
                fraction = BigDecimal.ONE;
            }
            if (cal.isWorkday(cur)) {
                d = d.add(fraction);
                // 不再 setScale：fraction 本身就是 0/0.5/1.0 粒度，累加结果天然是 0.5 倍数，
                // 保留原始精度到 3 位即可（LeaveDaysCalculator.divide 已用 scale=3）
            }
            cur = cur.plusDays(1);
        }
        return new Result(d, cal.warning);
    }

    /**
     * 按「固定天数」反推结束日期：自然日 = 开始日 + 天数 - 1；工作日按工作日历向后累计。
     * 城市政策类假期（婚假/产假/陪产假）用它自动带出结束日期。
     */
    public LocalDate endDateFor(LocalDate start, BigDecimal days, String dayMode) {
        if (start == null || days == null || days.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "请假天数必须大于 0");
        }
        int total = days.setScale(0, RoundingMode.CEILING).intValue();
        if (!MODE_WORKDAY.equalsIgnoreCase(dayMode)) {
            return start.plusDays(total - 1L);
        }
        WorkCalendar cal = loadCalendar(start, start.plusDays(total * 4L + 30L));
        LocalDate cur = start;
        int counted = 0;
        while (true) {
            if (cal.isWorkday(cur)) {
                counted++;
                if (counted >= total) {
                    return cur;
                }
            }
            cur = cur.plusDays(1);
        }
    }

    private String normalizePeriod(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String p = raw.trim().toUpperCase();
        if (!PERIOD_AM.equals(p) && !PERIOD_PM.equals(p)) {
            throw new BizException(EhrErrorCode.LEAVE_DATE_INVALID, "半天标识只能是 AM 或 PM");
        }
        return p;
    }

    private WorkCalendar loadCalendar(LocalDate from, LocalDate to) {
        Set<LocalDate> holidays = new HashSet<>();
        Set<LocalDate> extra = new HashSet<>();
        List<LocalDate> h = calendarMapper.holidays(from, to);
        if (h != null) {
            holidays.addAll(h);
        }
        List<LocalDate> e = calendarMapper.extraWorkdays(from, to);
        if (e != null) {
            extra.addAll(e);
        }
        String warning = holidays.isEmpty() && extra.isEmpty()
                ? "未配置工作日历，已按周末（周六/周日为休息日）计算" : null;
        return new WorkCalendar(holidays, extra, warning);
    }

    private record WorkCalendar(Set<LocalDate> holidays, Set<LocalDate> extraWorkdays, String warning) {
        boolean isWorkday(LocalDate d) {
            if (extraWorkdays.contains(d)) {
                return true;
            }
            if (holidays.contains(d)) {
                return false;
            }
            DayOfWeek w = d.getDayOfWeek();
            return w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY;
        }
    }
}
