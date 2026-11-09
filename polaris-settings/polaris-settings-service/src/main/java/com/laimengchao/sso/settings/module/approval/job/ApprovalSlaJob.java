package com.laimengchao.sso.settings.module.approval.job;

import com.laimengchao.sso.settings.module.approval.service.ApprovalOpsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审批 SLA 超时提醒。
 * <p>
 * 默认每小时整点扫描一次（approval.sla.cron 可覆盖）；同一实例每天只提醒一次，
 * 去重逻辑在 {@link ApprovalOpsService#scanSla()} 里按 appr_action 的当天流水判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalSlaJob {

    private final ApprovalOpsService opsService;

    @Scheduled(cron = "${approval.sla.cron:0 5 * * * ?}")
    public void scan() {
        try {
            opsService.scanSla();
        } catch (Exception e) {
            log.error("审批 SLA 扫描失败：{}", e.getMessage(), e);
        }
    }
}
