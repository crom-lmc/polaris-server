package com.laimengchao.sso.settings.schedule;

import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import com.laimengchao.sso.settings.module.approval.service.ApprovalOpsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 审批 SLA 超时提醒。
 * <p>
 * 触发由调度中心配置（cron 0 5 * * * ?）；同一实例每天只提醒一次，
 * 去重逻辑在 {@link ApprovalOpsService#scanSla()} 里按 appr_action 的当天流水判断。
 */
@Component
@RequiredArgsConstructor
public class ApprovalSlaJob implements JobHandler {

    private final ApprovalOpsService opsService;

    @Override
    public String name() {
        return "approvalSla";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        opsService.scanSla();
        return JobResult.ok();
    }
}
