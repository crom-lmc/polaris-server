package com.laimengchao.sso.job.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.dto.JobRunRequest;
import com.laimengchao.sso.job.config.JobCenterProperties;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.entity.JobLog;
import com.laimengchao.sso.job.module.job.entity.JobRegistry;
import com.laimengchao.sso.job.module.job.mapper.JobInfoMapper;
import com.laimengchao.sso.job.module.job.mapper.JobLogMapper;
import com.laimengchao.sso.job.module.job.mapper.JobRegistryMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 任务派发：选实例 → 调 {@code POST /internal/jobs/run} → 写 {@code job_log}。
 * <p>
 * 中心只传触发信息与参数，业务判断全在应用侧。执行器地址优先取配置 {@code job.apps}，
 * 未配置时回退到心跳在线的注册实例。
 */
@Slf4j
@Service
public class JobDispatchService {

    private static final String RUN_PATH = "/internal/jobs/run";
    private static final int MAX_TEXT = 2000;
    private static final int MAX_HTTP_BODY = 500;

    private final JobInfoMapper jobInfoMapper;
    private final JobRegistryMapper registryMapper;
    private final JobLogMapper jobLogMapper;
    private final JobCenterProperties properties;
    private final ObjectMapper objectMapper;
    private final JobAlarmService alarmService;

    private final Map<Long, Integer> running = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> roundRobin = new ConcurrentHashMap<>();
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "job-retry");
        thread.setDaemon(true);
        return thread;
    });

    public JobDispatchService(JobInfoMapper jobInfoMapper,
                              JobRegistryMapper registryMapper,
                              JobLogMapper jobLogMapper,
                              JobCenterProperties properties,
                              ObjectMapper objectMapper,
                              JobAlarmService alarmService) {
        this.jobInfoMapper = jobInfoMapper;
        this.registryMapper = registryMapper;
        this.jobLogMapper = jobLogMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.alarmService = alarmService;
    }

    /**
     * 阻塞策略判定用：同一任务是否还有执行中的实例。
     * 本机内存命中即可；未命中再查 job_log 里未回写的执行中记录，让 SERIAL/COVER 在多实例下同样生效。
     */
    public boolean isRunning(JobInfo job) {
        if (job == null || job.getId() == null) {
            return false;
        }
        if (running.getOrDefault(job.getId(), 0) > 0) {
            return true;
        }
        int timeoutSec = job.getTimeoutSec() == null ? 300 : Math.max(1, job.getTimeoutSec());
        try {
            LambdaQueryWrapper<JobLog> qw = new LambdaQueryWrapper<>();
            qw.eq(JobLog::getJobId, job.getId())
                    .eq(JobLog::getStatus, 0)
                    .ge(JobLog::getStartedAt, LocalDateTime.now().minusSeconds(timeoutSec));
            return jobLogMapper.selectCount(qw) > 0;
        } catch (Exception e) {
            // 查询失败时按未运行处理，避免因日志表异常导致任务永久不调度
            log.debug("跨实例运行状态查询失败，按未运行处理：job={} reason={}", job.getId(), e.getMessage());
            return false;
        }
    }

    /** 手动执行端点用：按 ID 取任务 */
    public JobInfo getJob(Long jobId) {
        return jobId == null ? null : jobInfoMapper.selectById(jobId);
    }

    /** 同步执行一个任务（由 JobScheduler 的线程池调用） */
    public void run(JobInfo job, LocalDateTime plannedAt) {
        running.merge(job.getId(), 1, Integer::sum);
        try {
            executePlan(job, plannedAt, 0);
        } catch (Exception e) {
            log.error("任务派发异常：job={} handler={}", job.getId(), job.getHandler(), e);
        } finally {
            running.computeIfPresent(job.getId(), (key, value) -> value <= 1 ? null : value - 1);
            advanceFixedDelay(job);
        }
    }

    private void executePlan(JobInfo job, LocalDateTime plannedAt, int retryIndex) {
        List<String> targets = resolveTargets(job.getAppCode());
        if (targets.isEmpty()) {
            writeFinishedLog(job, plannedAt, "", retryIndex, "无可用的执行器地址");
            if (!maybeRetry(job, plannedAt, retryIndex)) {
                alarmService.onJobFailure(job, 2, "无可用的执行器地址");
            }
            return;
        }

        String strategy = job.getRouteStrategy() == null ? "ROUND" : job.getRouteStrategy();
        int status;
        if ("BROADCAST".equals(strategy)) {
            status = 1;
            for (String target : targets) {
                int current = executeOnce(job, plannedAt, target, retryIndex);
                if (current != 1) {
                    status = current;
                }
            }
        } else if ("FAILOVER".equals(strategy)) {
            status = 3;
            for (String target : targets) {
                status = executeOnce(job, plannedAt, target, retryIndex);
                if (status == 1) {
                    break;
                }
            }
        } else {
            status = executeOnce(job, plannedAt, pick(job.getAppCode(), strategy, targets), retryIndex);
        }

        if (status == 1) {
            alarmService.onJobSuccess(job);
        } else if (!maybeRetry(job, plannedAt, retryIndex)) {
            alarmService.onJobFailure(job, status, null);
        }
    }

    private List<String> resolveTargets(String appCode) {
        String configured = properties.getApps().get(appCode);
        if (StringUtils.hasText(configured)) {
            return List.of(stripTrailingSlash(configured.trim()));
        }
        LambdaQueryWrapper<JobRegistry> qw = new LambdaQueryWrapper<>();
        qw.eq(JobRegistry::getAppCode, appCode)
                .ge(JobRegistry::getLastHeartbeat,
                        LocalDateTime.now().minusSeconds(JobRegistryService.ONLINE_WINDOW_SECONDS))
                .orderByAsc(JobRegistry::getLastHeartbeat);
        return registryMapper.selectList(qw).stream()
                .map(JobRegistry::getAddress)
                .filter(StringUtils::hasText)
                .distinct()
                .map(address -> "http://" + address.trim())
                .toList();
    }

    private String pick(String appCode, String strategy, List<String> targets) {
        int size = targets.size();
        if (size == 1) {
            return targets.get(0);
        }
        return switch (strategy) {
            case "RANDOM" -> targets.get(ThreadLocalRandom.current().nextInt(size));
            case "FIRST" -> targets.get(0);
            default -> {
                int index = roundRobin.computeIfAbsent(appCode, key -> new AtomicInteger()).getAndIncrement();
                yield targets.get(Math.floorMod(index, size));
            }
        };
    }

    private int executeOnce(JobInfo job, LocalDateTime plannedAt, String baseUrl, int retryIndex) {
        int timeoutSec = job.getTimeoutSec() == null ? 300 : job.getTimeoutSec();
        JobLog logRow = new JobLog();
        logRow.setJobId(job.getId());
        logRow.setTriggerType(retryIndex == 0 ? "SCHEDULE" : "RETRY");
        logRow.setPlannedAt(plannedAt);
        logRow.setStartedAt(LocalDateTime.now());
        logRow.setStatus(0);
        logRow.setRetryIndex(retryIndex);
        logRow.setExecutorAddress(baseUrl);
        jobLogMapper.insert(logRow);

        long start = System.currentTimeMillis();
        int status;
        String message;
        try {
            JobRunRequest request = new JobRunRequest();
            request.setAppCode(job.getAppCode());
            request.setHandler(job.getHandler());
            request.setLogId(logRow.getId());
            request.setFireTime(plannedAt);
            request.setParams(readParams(job.getParams()));

            JobResult result = client(baseUrl, timeoutSec).post()
                    .uri(RUN_PATH)
                    // X-Job-Token 由执行端点校验；X-Internal-Token 由各应用既有的 /internal/** 拦截器校验
                    .header("X-Job-Token", token())
                    .header("X-Internal-Token", token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(JobResult.class);

            if (result == null) {
                status = 2;
                message = "执行器返回空响应";
            } else if (result.isSuccess()) {
                status = 1;
                message = result.getMessage();
            } else {
                status = 2;
                message = result.getMessage();
            }
        } catch (RestClientResponseException e) {
            status = 2;
            message = "HTTP " + e.getStatusCode().value() + "：" + abbreviate(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            status = 3;
            message = "调用不可达或超时（" + timeoutSec + "s）：" + e.getMessage();
        } catch (Exception e) {
            status = 2;
            message = e.getClass().getSimpleName() + "：" + e.getMessage();
        }

        int costMs = (int) (System.currentTimeMillis() - start);
        logRow.setStatus(status);
        logRow.setFinishedAt(LocalDateTime.now());
        logRow.setDurationMs(costMs);
        if (status == 1) {
            logRow.setOutput(truncate(message));
        } else {
            logRow.setErrorMsg(truncate(message));
        }
        jobLogMapper.updateById(logRow);
        return status;
    }

    private void writeFinishedLog(JobInfo job, LocalDateTime plannedAt, String address,
                                  int retryIndex, String error) {
        JobLog logRow = new JobLog();
        logRow.setJobId(job.getId());
        logRow.setTriggerType(retryIndex == 0 ? "SCHEDULE" : "RETRY");
        logRow.setPlannedAt(plannedAt);
        logRow.setStartedAt(LocalDateTime.now());
        logRow.setFinishedAt(LocalDateTime.now());
        logRow.setStatus(2);
        logRow.setDurationMs(0);
        logRow.setRetryIndex(retryIndex);
        logRow.setExecutorAddress(address);
        logRow.setErrorMsg(truncate(error));
        jobLogMapper.insert(logRow);
        log.warn("任务无可执行实例：job={} app={} handler={}", job.getId(), job.getAppCode(), job.getHandler());
    }

    /** FIXED_DELAY：认领时已把 next_fire_time 置空，这里按「结束后 + 间隔」写回 */
    private void advanceFixedDelay(JobInfo job) {
        if (!"FIXED_DELAY".equals(job.getTriggerType()) || job.getIntervalSec() == null) {
            return;
        }
        try {
            LambdaUpdateWrapper<JobInfo> uw = new LambdaUpdateWrapper<>();
            uw.eq(JobInfo::getId, job.getId())
                    .isNull(JobInfo::getNextFireTime)
                    .set(JobInfo::getNextFireTime, LocalDateTime.now().plusSeconds(job.getIntervalSec()));
            jobInfoMapper.update(null, uw);
        } catch (Exception e) {
            log.warn("写回固定延迟任务的下次触发时间失败：job={} reason={}", job.getId(), e.getMessage());
        }
    }

    /** @return true 表示已排入重试，false 表示重试次数已用尽 */
    private boolean maybeRetry(JobInfo job, LocalDateTime plannedAt, int retryIndex) {
        int maxRetry = job.getRetryTimes() == null ? 0 : job.getRetryTimes();
        if (retryIndex >= maxRetry) {
            return false;
        }
        int intervalSec = job.getRetryIntervalSec() == null ? 30 : Math.max(0, job.getRetryIntervalSec());
        retryScheduler.schedule(() -> {
            try {
                executePlan(job, plannedAt, retryIndex + 1);
            } catch (Exception e) {
                log.warn("任务重试异常：job={} retryIndex={} reason={}", job.getId(), retryIndex + 1, e.getMessage());
            }
        }, intervalSec, TimeUnit.SECONDS);
        return true;
    }

    private RestClient client(String baseUrl, int timeoutSec) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(Math.max(1, timeoutSec)));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    private String token() {
        String token = properties.getExecutor() == null ? null : properties.getExecutor().getToken();
        return token == null ? "" : token.trim();
    }

    private Map<String, String> readParams(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> params = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
            });
            return params == null ? Map.of() : params;
        } catch (Exception e) {
            log.warn("任务参数解析失败，按空处理：{}", e.getMessage());
            return Map.of();
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_TEXT ? text : text.substring(0, MAX_TEXT);
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_HTTP_BODY ? text : text.substring(0, MAX_HTTP_BODY);
    }

    private String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @PreDestroy
    public void shutdown() {
        retryScheduler.shutdownNow();
    }
}
