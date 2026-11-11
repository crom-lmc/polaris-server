package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.reminder.service.ReminderService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 每天 09:00（东八区）生成试用期到期、合同到期提醒。
 * <p>
 * 触发由调度中心配置（cron 0 0 9 * * ?），多实例只跑一次由中心选主 + 乐观锁保证。
 */
@Component
@RequiredArgsConstructor
public class ReminderJob implements JobHandler {

    private final ReminderService reminderService;

    @Override
    public String name() {
        return "reminder";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        reminderService.generateDaily();
        reminderService.generateContractDaily();
        return JobResult.ok();
    }
}
