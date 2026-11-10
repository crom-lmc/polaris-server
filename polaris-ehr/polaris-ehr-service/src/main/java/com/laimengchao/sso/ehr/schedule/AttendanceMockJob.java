package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.attendance.service.AttendanceMockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 每日 01:00 生成前一天的模拟考勤数据，受 ehr.attendance.mock.enabled 开关控制。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttendanceMockJob {

    private final AttendanceMockService attendanceMockService;

    @Value("${ehr.attendance.mock.enabled:false}")
    private boolean enabled;

    @Scheduled(cron = "0 0 1 * * ?", zone = "Asia/Shanghai")
    public void generateYesterday() {
        if (!enabled) {
            return;
        }
        try {
            attendanceMockService.run(LocalDate.now().minusDays(1));
        } catch (Exception e) {
            log.error("模拟考勤定时任务失败", e);
        }
    }
}
