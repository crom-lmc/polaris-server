package com.laimengchao.sso.job.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.laimengchao.sso.job.config.JobCenterProperties;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.mapper.JobInfoMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 到期扫描：只有主节点扫描，乐观锁认领后交给派发线程池。
 * <p>
 * 认领时就把 {@code next_fire_time} 推进（或置空），保证「派发出去但中心崩了」不会在同一时刻反复触发。
 */
@Slf4j
@Service
public class JobScheduler {

    private static final int BATCH_SIZE = 50;
    private static final AtomicInteger THREAD_SEQ = new AtomicInteger();

    private final JobInfoMapper jobInfoMapper;
    private final JobDispatchService dispatchService;
    private final JobLeaderService leaderService;
    private final JobCenterProperties properties;
    private final CronService cronService;

    private final ExecutorService dispatcher = Executors.newFixedThreadPool(8, r -> {
        Thread thread = new Thread(r, "job-dispatch-" + THREAD_SEQ.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    public JobScheduler(JobInfoMapper jobInfoMapper, JobDispatchService dispatchService,
                        JobLeaderService leaderService, JobCenterProperties properties,
                        CronService cronService) {
        this.jobInfoMapper = jobInfoMapper;
        this.dispatchService = dispatchService;
        this.leaderService = leaderService;
        this.properties = properties;
        this.cronService = cronService;
    }

    @Scheduled(fixedDelayString = "${job.scheduler.scan-interval-ms:1000}", initialDelay = 3000)
    public void scan() {
        if (!properties.getScheduler().isEnabled() || !leaderService.isLeader()) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            LambdaQueryWrapper<JobInfo> qw = new LambdaQueryWrapper<>();
            qw.eq(JobInfo::getStatus, 1)
                    .isNotNull(JobInfo::getNextFireTime)
                    .le(JobInfo::getNextFireTime, now)
                    .orderByAsc(JobInfo::getNextFireTime)
                    .last("limit " + BATCH_SIZE);
            List<JobInfo> due = jobInfoMapper.selectList(qw);
            for (JobInfo job : due) {
                try {
                    claimAndDispatch(job, now);
                } catch (Exception e) {
                    log.error("任务认领失败：job={} handler={}", job.getId(), job.getHandler(), e);
                }
            }
            recoverStuckFixedDelay(now);
        } catch (Exception e) {
            log.warn("调度扫描失败：{}", e.getMessage());
        }
    }

    private void claimAndDispatch(JobInfo job, LocalDateTime now) {
        LocalDateTime planned = job.getNextFireTime();
        LocalDateTime next = "FIXED_DELAY".equals(job.getTriggerType())
                ? null : computeNextFire(job, planned.isAfter(now) ? planned : now);
        int claimedVersion = tryClaim(job, next, now);
        if (claimedVersion < 0) {
            return;
        }
        job.setLockVersion(claimedVersion);
        job.setNextFireTime(next);

        if (dispatchService.isRunning(job)) {
            if ("SERIAL".equals(job.getBlockStrategy())) {
                // 还上一次的「债」：退回原触发时间，等本次结束后立刻补跑
                restoreNextFire(job, planned, claimedVersion);
                log.debug("任务仍在执行，按 SERIAL 顺延：job={}", job.getId());
            } else if ("COVER".equals(job.getBlockStrategy())) {
                log.warn("COVER 暂不支持强制取消，按 DISCARD 跳过：job={}", job.getId());
            }
            return;
        }
        dispatcher.submit(() -> dispatchService.run(job, planned));
    }

    /** 乐观锁认领：lock_version 不匹配说明别的实例已经领走；返回认领后的版本号，-1 表示未认领 */
    private int tryClaim(JobInfo job, LocalDateTime next, LocalDateTime now) {
        int version = job.getLockVersion() == null ? 0 : job.getLockVersion();
        LambdaUpdateWrapper<JobInfo> uw = new LambdaUpdateWrapper<>();
        uw.eq(JobInfo::getId, job.getId())
                .eq(JobInfo::getLockVersion, version)
                .set(JobInfo::getLockVersion, version + 1)
                .set(JobInfo::getNextFireTime, next)
                // 显式刷新，作为「认领时刻」用于卡死任务的恢复判定（update(null, wrapper) 不会触发自动填充）
                .set(JobInfo::getUpdatedAt, now);
        return jobInfoMapper.update(null, uw) > 0 ? version + 1 : -1;
    }

    /** SERIAL 回退时必须带上认领后的版本号，避免覆盖别的实例已推进的时间点 */
    private void restoreNextFire(JobInfo job, LocalDateTime planned, int claimedVersion) {
        LambdaUpdateWrapper<JobInfo> uw = new LambdaUpdateWrapper<>();
        uw.eq(JobInfo::getId, job.getId())
                .eq(JobInfo::getLockVersion, claimedVersion)
                .set(JobInfo::getNextFireTime, planned);
        jobInfoMapper.update(null, uw);
    }

    /**
     * 修复「固定延迟任务认领后主节点崩溃」：认领时 next_fire_time 被置空，若迟迟没写回就再也不会触发。
     * 只在确认跨实例都没有执行中的记录、且超过最大可能执行时长后才恢复，避免把正在跑的任务重复拉起。
     */
    private void recoverStuckFixedDelay(LocalDateTime now) {
        try {
            LambdaQueryWrapper<JobInfo> qw = new LambdaQueryWrapper<>();
            qw.eq(JobInfo::getStatus, 1)
                    .eq(JobInfo::getTriggerType, "FIXED_DELAY")
                    .isNull(JobInfo::getNextFireTime)
                    .lt(JobInfo::getUpdatedAt, now.minusSeconds(60))
                    .last("limit " + BATCH_SIZE);
            for (JobInfo job : jobInfoMapper.selectList(qw)) {
                if (job.getUpdatedAt() != null
                        && job.getUpdatedAt().isAfter(now.minusSeconds(guardSeconds(job)))) {
                    continue;
                }
                if (dispatchService.isRunning(job)) {
                    continue;
                }
                int interval = job.getIntervalSec() == null || job.getIntervalSec() <= 0
                        ? 60 : job.getIntervalSec();
                LambdaUpdateWrapper<JobInfo> uw = new LambdaUpdateWrapper<>();
                uw.eq(JobInfo::getId, job.getId())
                        .isNull(JobInfo::getNextFireTime)
                        .set(JobInfo::getNextFireTime, now.plusSeconds(interval))
                        .set(JobInfo::getUpdatedAt, now);
                if (jobInfoMapper.update(null, uw) > 0) {
                    log.warn("发现卡死的固定延迟任务，已恢复调度：job={} handler={}", job.getId(), job.getHandler());
                }
            }
        } catch (Exception e) {
            log.warn("固定延迟任务恢复扫描失败：{}", e.getMessage());
        }
    }

    /** 单次执行最长耗时 × 可能的重试次数，再留一次冗余，作为「肯定不是在跑」的判定窗口 */
    private long guardSeconds(JobInfo job) {
        long timeout = job.getTimeoutSec() == null ? 300 : Math.max(1, job.getTimeoutSec());
        int retry = job.getRetryTimes() == null ? 0 : Math.max(0, job.getRetryTimes());
        return Math.max(60, timeout) * (retry + 2L);
    }

    /**
     * 计算下一次触发时间：
     * CRON 从基准时间取下一次；FIXED_RATE 按计划时间等差数列推进（落后则跳到 now 之后，不补跑）。
     */
    public LocalDateTime computeNextFire(JobInfo job, LocalDateTime base) {
        if ("CRON".equals(job.getTriggerType())) {
            try {
                return cronService.nextFireTime(job.getCronExpr(), base);
            } catch (Exception e) {
                log.warn("CRON 任务取下次触发时间失败，1 分钟后重试：job={} cron={} reason={}",
                        job.getId(), job.getCronExpr(), e.getMessage());
                return base.plusMinutes(1);
            }
        }
        int interval = job.getIntervalSec() == null ? 0 : job.getIntervalSec();
        if (interval <= 0) {
            return base.plusMinutes(1);
        }
        return base.plusSeconds(interval);
    }

    @PreDestroy
    public void shutdown() {
        dispatcher.shutdownNow();
    }
}
