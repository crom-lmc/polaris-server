package com.laimengchao.sso.cms.schedule;

import com.laimengchao.sso.cms.module.content.service.ArticleService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 定时发布调度：扫描到点的「待发布」文章。
 * 触发由调度中心配置（FIXED_DELAY 30s）。
 */
@Component
@RequiredArgsConstructor
public class ScheduledPublishJob implements JobHandler {

    private final ArticleService articleService;

    @Override
    public String name() {
        return "scheduledPublish";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        articleService.publishDueScheduled();
        return JobResult.ok();
    }
}
