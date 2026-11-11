package com.laimengchao.sso.job.module.job.service;

import com.laimengchao.sso.job.config.JobCenterProperties;
import com.laimengchao.sso.job.module.job.mapper.JobLockMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 租约选主：只有一个中心实例扫描并派发任务。
 * <p>
 * 租约 45 秒、每 15 秒续约。进程被 kill 时来不及释放，最坏 45 秒后其他实例接管。
 * 选主只是「少做无用功」，真正的防重靠 {@code job_info.lock_version} 乐观锁。
 */
@Slf4j
@Service
public class JobLeaderService implements InitializingBean, DisposableBean {

    private static final String LOCK_NAME = "scheduler";
    private static final long LOCK_ROW_ID = 1L;

    private final JobLockMapper lockMapper;
    private final JobCenterProperties properties;
    private final String owner;

    private final ScheduledExecutorService renewer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "job-leader-renew");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean leader;

    public JobLeaderService(JobLockMapper lockMapper, JobCenterProperties properties, Environment environment) {
        this.lockMapper = lockMapper;
        this.properties = properties;
        this.owner = buildOwner(environment);
    }

    @Override
    public void afterPropertiesSet() {
        if (!properties.getScheduler().isEnabled()) {
            log.info("调度扫描已关闭（job.scheduler.enabled=false），本实例不参与选主");
            return;
        }
        lockMapper.ensureRow();
        int renewSeconds = Math.max(1, properties.getScheduler().getRenewSeconds());
        renewer.scheduleWithFixedDelay(this::renewSafely, 0, renewSeconds, TimeUnit.SECONDS);
    }

    private void renewSafely() {
        try {
            int leaseSeconds = Math.max(1, properties.getScheduler().getLeaseSeconds());
            boolean acquired = lockMapper.acquire(owner, leaseSeconds) > 0;
            if (acquired && !leader) {
                log.info("已获得调度主节点：owner={}", owner);
            } else if (!acquired && leader) {
                log.warn("调度主节点租约丢失：owner={}", owner);
            }
            leader = acquired;
        } catch (Exception e) {
            leader = false;
            log.warn("调度选主失败，本轮不参与派发：{}", e.getMessage());
        }
    }

    public boolean isLeader() {
        return leader;
    }

    public String owner() {
        return owner;
    }

    @Override
    public void destroy() {
        try {
            if (leader) {
                lockMapper.release(owner);
            }
        } catch (Exception e) {
            log.debug("释放调度锁失败（忽略）：{}", e.getMessage());
        }
        renewer.shutdownNow();
    }

    private static String buildOwner(Environment environment) {
        String port = environment.getProperty("local.server.port",
                environment.getProperty("server.port", ""));
        try {
            String host = InetAddress.getLocalHost().getHostAddress();
            return host + ":" + port + ":" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "unknown:" + port + ":" + ProcessHandle.current().pid();
        }
    }
}
