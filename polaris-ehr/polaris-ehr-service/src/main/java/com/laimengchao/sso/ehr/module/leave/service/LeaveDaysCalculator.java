package com.laimengchao.sso.ehr.module.leave.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.ehr.common.EhrErrorCode;
import com.laimengchao.sso.ehr.module.leave.mapper.WorkCalendarMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 请假天数计算：支持半天与工作日模式，P0-1 的额度冻结 / 销假重算共用。
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

    private final WorkCalendarMapper calendarMapper;

    public record Result(BigDecimal days, String warning) {
    }

    /**
     * @param startPeriod 开始半天：AM（上午，计 1 天） / PM（下午，计 0.5 天）
     * @param endPeriod   结束半天：PM（计 1 天） / AM（计 0.5 天）
     */
    public Result calculate(LocalDate start, String startPeriod, LocalDate end, String endPeriod, String dayMode) {
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
                // 工作日模式下按 0.5 天粒度取整，避免出现 0.25 之类不可用额度
                d = new BigDecimal(d.setScale(1, java.math.RoundingMode.HALF_UP).toPlainString());
            }
            cur = cur.plusDays(1);
        }
        return new Result(d, cal.warning);
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
