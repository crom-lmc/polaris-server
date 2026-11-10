package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.attendance.service.AttendanceCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 下一年工作日历维护提醒：每年 11 月起，若下一年日历尚未一键更新，
 * 每天向指定接收人（未配置则 HR 部门）发送一条维护待办，直到完成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarMaintainRemindJob {

    private final AttendanceCalendarService calendarService;

    @Scheduled(cron = "${ehr.attendance.calendar-maintain-cron:0 30 9 * * *}")
    public void remind() {
        try {
            calendarService.remindNextYearCalendarIfNeeded();
        } catch (Exception e) {
            log.error("工作日历维护提醒任务失败: {}", e.getMessage(), e);
        }
    }
}
