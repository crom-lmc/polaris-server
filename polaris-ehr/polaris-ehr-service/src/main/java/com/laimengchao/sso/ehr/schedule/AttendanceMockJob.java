package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.integration.EmbedPointReader;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceMockService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日 02:00 生成前一天的模拟考勤数据，受配置中心埋点 ehr.attendance.mock.enabled 开关控制。
 * <p>
 * 触发由调度中心配置（cron 0 0 2 * * ?）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceMockJob implements JobHandler {

    /** 配置中心埋点：模拟考勤定时任务开关 */
    private static final String EMBED_MOCK_ENABLED = "ehr.attendance.mock.enabled";

    private final AttendanceMockService attendanceMockService;
    private final EmbedPointReader embedPointReader;

    @Override
    public String name() {
        return "attendanceMock";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        if (!embedPointReader.flag(EMBED_MOCK_ENABLED, "enabled", false)) {
            return JobResult.ok("模拟考勤开关未开启，跳过");
        }
        attendanceMockService.run(LocalDate.now().minusDays(1));
        return JobResult.ok();
    }
}
