package com.laimengchao.sso.settings.schedule;

import com.laimengchao.sso.settings.module.approval.service.ApprovalCallbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 终审回调重试：每 60 秒扫一次待重试记录，退避间隔 1/2/4/8/16/30/30… 分钟。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalCallbackRetryJob {

    private final ApprovalCallbackService callbackService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void retry() {
        try {
            callbackService.retryFailed();
        } catch (Exception e) {
            log.error("审批回调重试任务失败：{}", e.getMessage(), e);
        }
    }
}
