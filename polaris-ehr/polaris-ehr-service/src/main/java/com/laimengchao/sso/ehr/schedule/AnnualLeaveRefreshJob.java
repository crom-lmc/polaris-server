package com.laimengchao.sso.ehr.schedule;

import com.laimengchao.sso.ehr.module.leave.service.AnnualLeaveService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 年假额度每日刷新：在职员工缺失当年额度则补建，并处理当天跨档（司龄/工龄变化）补差。
 * <p>
 * 触发由调度中心配置（cron 0 30 2 * * ?），本类只提供 handler。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnualLeaveRefreshJob implements JobHandler {

    private final AnnualLeaveService annualLeaveService;

    @Override
    public String name() {
        return "annualLeaveRefresh";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        annualLeaveService.dailyRefresh();
        return JobResult.ok();
    }

}
