package com.laimengchao.sso.job.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.job.config.JobCenterProperties;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.mapper.JobInfoMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 启动重算：停机期间错过的任务不在启动瞬间集中补跑。
 * <p>
 * 只处理「next_fire_time 为空」与「晚了超过 60 秒」的任务，按当前时间重算；
 * 由主节点执行，未选上主时最多重试 6 次。
 */
@Slf4j
@Component
public class JobStartupBootstrap {

    /** 未选上主时每 5 秒重试，最多 150 秒：覆盖上一个实例被强杀后最长 45 秒的租约残留 */
    private static final int MAX_ATTEMPTS = 30;
    private static final long DELAY_SECONDS = 5;

    private final JobInfoMapper jobInfoMapper;
    private final JobScheduler jobScheduler;
    private final JobLeaderService leaderService;
    private final JobCenterProperties properties;
    private final AtomicInteger attempts = new AtomicInteger();

    private final ScheduledExecutorService runner = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "job-startup-bootstrap");
        thread.setDaemon(true);
        return thread;
    });

    public JobStartupBootstrap(JobInfoMapper jobInfoMapper, JobScheduler jobScheduler,
                               JobLeaderService leaderService, JobCenterProperties properties) {
        this.jobInfoMapper = jobInfoMapper;
        this.jobScheduler = jobScheduler;
        this.leaderService = leaderService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void schedule() {
        if (!properties.getScheduler().isEnabled()) {
            return;
        }
        runner.schedule(this::runOnce, DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void runOnce() {
        if (!leaderService.isLeader()) {
            if (attempts.incrementAndGet() < MAX_ATTEMPTS) {
                runner.schedule(this::runOnce, DELAY_SECONDS, TimeUnit.SECONDS);
            }
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            LambdaQueryWrapper<JobInfo> qw = new LambdaQueryWrapper<>();
            qw.eq(JobInfo::getStatus, 1)
                    .and(w -> w.isNull(JobInfo::getNextFireTime)
                            .or()
                            .lt(JobInfo::getNextFireTime, now.minusSeconds(60)));
            List<JobInfo> jobs = jobInfoMapper.selectList(qw);
            for (JobInfo job : jobs) {
                LocalDateTime next = "FIXED_DELAY".equals(job.getTriggerType())
                        ? now.plusSeconds(job.getIntervalSec() == null ? 60 : job.getIntervalSec())
                        : jobScheduler.computeNextFire(job, now);
                LambdaUpdateWrapper<JobInfo> uw = new LambdaUpdateWrapper<>();
                uw.eq(JobInfo::getId, job.getId()).set(JobInfo::getNextFireTime, next);
                jobInfoMapper.update(null, uw);
                log.info("启动重算下次触发时间：job={} handler={} next={}", job.getId(), job.getHandler(), next);
            }
            if (!jobs.isEmpty()) {
                log.info("启动重算完成，共 {} 个任务", jobs.size());
            }
        } catch (Exception e) {
            log.warn("启动重算下次触发时间失败：{}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        runner.shutdownNow();
    }
}
