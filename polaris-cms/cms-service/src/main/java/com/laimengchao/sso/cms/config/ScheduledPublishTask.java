package com.laimengchao.sso.cms.config;

import com.laimengchao.sso.cms.module.content.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时发布调度：每 30 秒扫描一次到点的「待发布」文章 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledPublishTask {

    private final ArticleService articleService;

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void publishDue() {
        try {
            articleService.publishDueScheduled();
        } catch (Exception e) {
            // 调度失败不能打断下一次扫描
            log.error("定时发布扫描失败", e);
        }
    }
}
