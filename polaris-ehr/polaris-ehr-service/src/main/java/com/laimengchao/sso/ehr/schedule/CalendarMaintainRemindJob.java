package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.attendance.service.AttendanceCalendarService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 下一年工作日历维护提醒：每年 11 月起，若下一年日历尚未一键更新，
 * 每天向指定接收人（未配置则 HR 部门）发送一条维护待办，直到完成。
 * 触发由调度中心配置（cron 0 30 9 * * ?）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarMaintainRemindJob implements JobHandler {

    private final AttendanceCalendarService calendarService;

    @Override
    public String name() {
        return "calendarMaintainRemind";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        calendarService.remindNextYearCalendarIfNeeded();
        return JobResult.ok();
    }
}
