package com.laimengchao.sso.ehr.module.reminder.job;

import com.laimengchao.sso.ehr.module.reminder.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天 09:00（东八区）生成试用期到期提醒；多实例由 ehr_job_lock 保证只跑一次 */
@Component
@RequiredArgsConstructor
public class ReminderJob {

    private final ReminderService reminderService;

    @Scheduled(cron = "0 0 9 * * ?", zone = "Asia/Shanghai")
    public void run() {
        reminderService.generateDaily();
        reminderService.generateContractDaily();
    }
}
