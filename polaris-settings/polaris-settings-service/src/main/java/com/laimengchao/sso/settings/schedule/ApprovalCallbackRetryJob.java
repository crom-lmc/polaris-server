package com.laimengchao.sso.settings.schedule;

import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import com.laimengchao.sso.settings.module.approval.service.ApprovalCallbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 终审回调重试：每 60 秒扫一次待重试记录，退避间隔 1/2/4/8/16/30/30… 分钟。
 * 触发由调度中心配置（FIXED_DELAY 60s）。
 */
@Component
@RequiredArgsConstructor
public class ApprovalCallbackRetryJob implements JobHandler {

    private final ApprovalCallbackService callbackService;

    @Override
    public String name() {
        return "approvalCallbackRetry";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        callbackService.retryFailed();
        return JobResult.ok();
    }
}
