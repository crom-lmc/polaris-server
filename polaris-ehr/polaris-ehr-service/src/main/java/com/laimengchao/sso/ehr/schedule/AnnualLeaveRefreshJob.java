package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.leave.service.AnnualLeaveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 年假额度每日刷新：在职员工缺失当年额度则补建，并处理当天跨档（司龄/工龄变化）补差。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnualLeaveRefreshJob {

    private final AnnualLeaveService annualLeaveService;

    @Scheduled(cron = "0 30 2 * * ?", zone = "Asia/Shanghai")
    public void refreshDaily() {
        annualLeaveService.dailyRefresh();
    }
}
