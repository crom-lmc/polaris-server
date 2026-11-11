package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.integration.EmbedPointReader;
import com.laimengchao.sso.ehr.module.attendance.service.AttendanceMockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日 01:00 生成前一天的模拟考勤数据，受配置中心埋点 ehr.attendance.mock.enabled 开关控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceMockJob {

    /** 配置中心埋点：模拟考勤定时任务开关 */
    private static final String EMBED_MOCK_ENABLED = "ehr.attendance.mock.enabled";

    private final AttendanceMockService attendanceMockService;
    private final EmbedPointReader embedPointReader;

    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Shanghai")
    public void generateYesterday() {
        if (!embedPointReader.flag(EMBED_MOCK_ENABLED, "enabled", false)) {
            return;
        }
        try {
            attendanceMockService.run(LocalDate.now().minusDays(1));
        } catch (Exception e) {
            log.error("模拟考勤定时任务失败", e);
        }
    }
}
