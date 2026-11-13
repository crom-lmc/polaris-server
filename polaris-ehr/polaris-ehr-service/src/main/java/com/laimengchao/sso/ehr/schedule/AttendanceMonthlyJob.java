package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.attendance.service.AttendanceDailyService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * 每日 04:00 兜底重算「上一个完整日历月」的全员月度考勤汇总。
 * <p>
 * 排在 {@link AttendanceDailyJob}（03:00）之后，针对上一整月做一次全量下沉，
 * 确保月底之后才审批通过的请假单 / 补卡 / 加班单等变更都能被汇总进去。
 * 已确认锁定（confirmed=1）的月份自动跳过，不会覆盖。
 * <p>
 * 示例：
 * 1 月 1 日 → 汇总去年 12 月（全员）
 * 4 月 1 日 → 汇总 3 月
 * 4 月 2 日 → 继续兜 3 月（4 月 5 号批准的 3 月 28 日请假也能兜到）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceMonthlyJob implements JobHandler {

    private final AttendanceDailyService dailyService;

    @Override
    public String name() {
        return "attendanceMonthly";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        YearMonth target;
        String monthParam = ctx.param("month");
        if (monthParam != null && !monthParam.isBlank()) {
            target = YearMonth.parse(monthParam);
        } else {
            target = YearMonth.now().minusMonths(1);
        }
        int updated = dailyService.recalcMonthAll(target);
        return JobResult.ok("月份=" + target + " 月度汇总全量重算，更新 " + updated + " 人");
    }
}
