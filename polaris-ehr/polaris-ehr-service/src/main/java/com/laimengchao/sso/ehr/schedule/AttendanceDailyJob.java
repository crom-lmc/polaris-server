package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.attendance.service.AttendanceDailyService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日 03:00 兜底重算昨天全员日考勤结果 + 月度汇总。
 * <p>
 * 与 {@link AttendanceMockJob} 独立（02:00 模拟考勤），不受 mock.enabled 开关控制。
 * 即使模拟考勤关闭、或真实打卡数据直接导入，也能保证日结果自动落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceDailyJob implements JobHandler {

    private final AttendanceDailyService dailyService;

    @Override
    public String name() {
        return "attendanceDaily";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        int rows = dailyService.generate(yesterday);
        return JobResult.ok("日期=" + yesterday + " 日结果重算完成，共 " + rows + " 行");
    }
}
