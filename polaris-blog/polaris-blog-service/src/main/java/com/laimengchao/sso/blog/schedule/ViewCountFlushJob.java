package com.laimengchao.sso.blog.schedule;

import com.laimengchao.sso.blog.module.article.service.ArticleService;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 阅读数落库任务：把 Redis 中累计的阅读数批量写入数据库，避免高频写库。
 * 触发由调度中心配置（FIXED_DELAY 300s）。
 */
@Component
@RequiredArgsConstructor
public class ViewCountFlushJob implements JobHandler {

    private final ArticleService articleService;

    @Override
    public String name() {
        return "viewCountFlush";
    }

    @Override
    public JobResult execute(JobContext ctx) {
        articleService.flushViewCounts();
        return JobResult.ok();
    }
}
