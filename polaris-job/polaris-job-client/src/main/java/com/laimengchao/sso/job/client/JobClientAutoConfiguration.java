package com.laimengchao.sso.job.client;

import com.laimengchao.sso.job.client.executor.JobDispatcher;
import com.laimengchao.sso.job.client.executor.JobExecutorController;
import com.laimengchao.sso.job.client.executor.JobRegistryReporter;
import com.laimengchao.sso.job.client.handler.JobHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 引入 job-client 即自动装配：
 * 1. {@link JobDispatcher} 收集应用内所有 {@link JobHandler}；
 * 2. {@link JobExecutorController} 暴露 POST /internal/jobs/run；
 * 3. {@link JobRegistryReporter} 向中心上报 Handler 清单与心跳。
 * <p>
 * 调度中心自身通过 {@code job.executor.enabled=false} 关闭执行器能力，避免自己注册成执行器。
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnProperty(prefix = "job.executor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JobDispatcher jobDispatcher(ObjectProvider<JobHandler> handlers) {
        return new JobDispatcher(handlers.orderedStream().toList());
    }

    @Bean
    @ConditionalOnMissingBean
    public JobExecutorController jobExecutorController(
            JobDispatcher jobDispatcher,
            @Value("${job.executor.token:${SSO_INTERNAL_TOKEN:}}") String token) {
        return new JobExecutorController(jobDispatcher, token);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobRegistryReporter jobRegistryReporter(JobDispatcher jobDispatcher,
                                                   Environment environment) {
        return new JobRegistryReporter(jobDispatcher, environment);
    }
}
