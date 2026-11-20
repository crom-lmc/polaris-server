package com.laimengchao.sso.settings.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步通知线程池：仅用于 SLA 扫描等批量站内信发送，避免定时任务线程被逐条发送阻塞。
 * 专用 bean（noticeAsyncExecutor）通过 @Async("noticeAsyncExecutor") 指定，不影响其它同步通知调用点。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("noticeAsyncExecutor")
    public Executor noticeAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        // 守护线程：应用关闭时不阻塞 JVM 退出；队列满时由调用线程执行，避免消息丢失
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "notice-async-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        executor.setThreadFactory(factory);
        executor.initialize();
        return executor;
    }
}
