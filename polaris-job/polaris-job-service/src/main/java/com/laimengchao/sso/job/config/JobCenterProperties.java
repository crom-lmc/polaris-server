package com.laimengchao.sso.job.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * 调度中心配置：应用基址、出站令牌、扫描参数。
 */
@Data
@ConfigurationProperties(prefix = "job")
public class JobCenterProperties {

    /** 内部端点（应用 → 中心）准入令牌 */
    private String internalToken;

    /** 执行器相关：job.executor.token 是中心 → 应用执行端点的 X-Job-Token */
    private Executor executor = new Executor();

    /** appCode → 应用基址；缺省时回退到执行器注册表里的在线实例 */
    private Map<String, String> apps = new HashMap<>();

    private Scheduler scheduler = new Scheduler();

    @Data
    public static class Executor {
        private boolean enabled;
        private String token;
    }

    @Data
    public static class Scheduler {
        private boolean enabled = true;
        private long scanIntervalMs = 1000;
        private int leaseSeconds = 45;
        private int renewSeconds = 15;
    }
}
