package com.laimengchao.sso.job.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.job.common.JobErrorCode;
import com.laimengchao.sso.job.module.job.dto.JobSaveRequest;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.mapper.JobInfoMapper;
import com.laimengchao.sso.job.module.job.vo.JobInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 任务定义服务：CRUD + 保存校验 + 下次触发时间计算。
 * <p>
 * 唯一标识 = appCode + handler；不保存 Feign 接口名/方法名，应用侧按 Handler Bean 名分发。
 */
@Slf4j
@Service
public class JobInfoService {

    private static final Pattern APP_CODE = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern HANDLER = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]{0,127}$");
    private static final Set<String> TRIGGER_TYPES = Set.of("CRON", "FIXED_RATE", "FIXED_DELAY");
    private static final Set<String> ROUTE_STRATEGIES = Set.of("ROUND", "FAILOVER", "RANDOM", "FIRST", "BROADCAST");
    private static final Set<String> BLOCK_STRATEGIES = Set.of("DISCARD", "SERIAL", "COVER");

    private final JobInfoMapper jobInfoMapper;
    private final JobRegistryService registryService;
    private final CronService cronService;
    private final ObjectMapper objectMapper;

    public JobInfoService(JobInfoMapper jobInfoMapper, JobRegistryService registryService,
                          CronService cronService, ObjectMapper objectMapper) {
        this.jobInfoMapper = jobInfoMapper;
        this.registryService = registryService;
        this.cronService = cronService;
        this.objectMapper = objectMapper;
    }

    public PageResult<JobInfoVO> list(long pageNo, long size, String keyword, String appCode,
                                      Integer status, String triggerType) {
        long page = Math.max(1, pageNo);
        long pageSize = Math.min(200, Math.max(1, size));
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;

        LambdaQueryWrapper<JobInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(StringUtils.hasText(appCode), JobInfo::getAppCode, appCode == null ? null : appCode.trim());
        qw.eq(status != null, JobInfo::getStatus, status);
        qw.eq(StringUtils.hasText(triggerType), JobInfo::getTriggerType,
                triggerType == null ? null : triggerType.trim().toUpperCase());
        if (kw != null) {
            qw.and(w -> w.like(JobInfo::getName, kw)
                    .or().like(JobInfo::getHandler, kw)
                    .or().like(JobInfo::getDescription, kw));
        }
        qw.orderByDesc(JobInfo::getCreatedAt);

        Page<JobInfo> result = jobInfoMapper.selectPage(new Page<>(page, pageSize), qw);
        Map<String, Set<String>> handlerCache = new HashMap<>();
        List<JobInfoVO> records = result.getRecords().stream()
                .map(job -> toVO(job, handlerCache))
                .toList();
        return PageResult.of(records, result.getTotal(), page, pageSize);
    }

    public JobInfoVO get(Long id) {
        return toVO(requireJob(id), new HashMap<>());
    }

    @Transactional
    public JobInfoVO create(JobSaveRequest request) {
        validate(request, null);
        JobInfo job = new JobInfo();
        apply(job, request);
        job.setLockVersion(0);
        jobInfoMapper.insert(job);
        log.info("创建任务：app={} handler={} trigger={}", job.getAppCode(), job.getHandler(), job.getTriggerType());
        return toVO(job, new HashMap<>());
    }

    @Transactional
    public JobInfoVO update(Long id, JobSaveRequest request) {
        JobInfo job = requireJob(id);
        validate(request, id);
        apply(job, request);
        jobInfoMapper.updateById(job);
        log.info("更新任务：id={} app={} handler={}", id, job.getAppCode(), job.getHandler());
        return toVO(job, new HashMap<>());
    }

    @Transactional
    public void delete(Long id) {
        requireJob(id);
        jobInfoMapper.deleteById(id);
    }

    @Transactional
    public JobInfoVO toggleStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 只能为 0 或 1");
        }
        JobInfo job = requireJob(id);
        job.setStatus(status);
        job.setNextFireTime(computeNextFire(job));
        jobInfoMapper.updateById(job);
        return toVO(job, new HashMap<>());
    }

    /* ---------------- 校验 ---------------- */

    private void validate(JobSaveRequest req, Long excludeId) {
        if (req == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请求体不能为空");
        }
        String appCode = requireText(req.getAppCode(), "应用编码");
        if (!APP_CODE.matcher(appCode).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "应用编码不合法（小写字母开头，仅小写字母/数字/中划线，2-32 位）");
        }
        String name = requireText(req.getName(), "任务名称");
        if (name.length() > 64) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务名称不能超过 64 个字符");
        }
        String handler = requireText(req.getHandler(), "Handler");
        if (!HANDLER.matcher(handler).matches()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "Handler 不合法（字母开头，仅字母/数字/点/下划线/中划线，最长 128 位）");
        }

        LambdaQueryWrapper<JobInfo> duplicate = new LambdaQueryWrapper<>();
        duplicate.eq(JobInfo::getAppCode, appCode).eq(JobInfo::getHandler, handler);
        if (excludeId != null) {
            duplicate.ne(JobInfo::getId, excludeId);
        }
        if (jobInfoMapper.selectCount(duplicate) > 0) {
            throw new BizException(JobErrorCode.JOB_DUPLICATED,
                    "应用 " + appCode + " 下已存在 Handler 为 " + handler + " 的任务");
        }

        String triggerType = requireText(req.getTriggerType(), "触发类型").toUpperCase();
        if (!TRIGGER_TYPES.contains(triggerType)) {
            throw new BizException(JobErrorCode.JOB_TRIGGER_INVALID,
                    "触发类型仅支持 CRON / FIXED_RATE / FIXED_DELAY");
        }
        req.setTriggerType(triggerType);
        if ("CRON".equals(triggerType)) {
            req.setCronExpr(cronService.normalize(req.getCronExpr()));
            // 试算未来 3 次，非法直接拒绝
            cronService.preview(req.getCronExpr());
            req.setIntervalSec(null);
        } else {
            if (req.getIntervalSec() == null || req.getIntervalSec() <= 0) {
                throw new BizException(JobErrorCode.JOB_TRIGGER_INVALID, "固定频率/延迟的间隔秒数必须大于 0");
            }
            req.setCronExpr(null);
        }

        String routeStrategy = StringUtils.hasText(req.getRouteStrategy())
                ? req.getRouteStrategy().trim().toUpperCase() : "ROUND";
        if (!ROUTE_STRATEGIES.contains(routeStrategy)) {
            throw new BizException(JobErrorCode.JOB_EXEC_CONFIG_INVALID, "不支持的路由策略：" + routeStrategy);
        }
        req.setRouteStrategy(routeStrategy);

        String blockStrategy = StringUtils.hasText(req.getBlockStrategy())
                ? req.getBlockStrategy().trim().toUpperCase() : "DISCARD";
        if (!BLOCK_STRATEGIES.contains(blockStrategy)) {
            throw new BizException(JobErrorCode.JOB_EXEC_CONFIG_INVALID, "不支持的阻塞策略：" + blockStrategy);
        }
        req.setBlockStrategy(blockStrategy);

        int timeoutSec = req.getTimeoutSec() == null ? 300 : req.getTimeoutSec();
        if (timeoutSec < 1 || timeoutSec > 86400) {
            throw new BizException(JobErrorCode.JOB_EXEC_CONFIG_INVALID, "超时秒数必须在 1-86400 之间");
        }
        req.setTimeoutSec(timeoutSec);

        int retryTimes = req.getRetryTimes() == null ? 0 : req.getRetryTimes();
        if (retryTimes < 0 || retryTimes > 10) {
            throw new BizException(JobErrorCode.JOB_EXEC_CONFIG_INVALID, "重试次数必须在 0-10 之间");
        }
        req.setRetryTimes(retryTimes);

        int retryIntervalSec = req.getRetryIntervalSec() == null ? 30 : req.getRetryIntervalSec();
        if (retryIntervalSec < 0 || retryIntervalSec > 86400) {
            throw new BizException(JobErrorCode.JOB_EXEC_CONFIG_INVALID, "重试间隔必须在 0-86400 秒之间");
        }
        req.setRetryIntervalSec(retryIntervalSec);

        int status = req.getStatus() == null ? 0 : req.getStatus();
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "状态只能为 0（暂停）或 1（运行）");
        }
        req.setStatus(status);

        checkHandler(appCode, handler);

        req.setAppCode(appCode);
        req.setName(name);
        req.setHandler(handler);
    }

    /** 应用有上报记录时，handler 必须在并集中；从未上报时放行（无法校验） */
    private void checkHandler(String appCode, String handler) {
        Set<String> known = registryService.knownHandlers(appCode);
        if (!known.isEmpty() && !known.contains(handler)) {
            throw new BizException(JobErrorCode.JOB_HANDLER_UNKNOWN,
                    "应用 " + appCode + " 未上报 Handler：" + handler + "，已上报清单：" + known);
        }
    }

    private void apply(JobInfo job, JobSaveRequest req) {
        job.setAppCode(req.getAppCode());
        job.setName(req.getName());
        job.setHandler(req.getHandler());
        job.setDescription(req.getDescription());
        job.setTriggerType(req.getTriggerType());
        job.setCronExpr(req.getCronExpr());
        job.setIntervalSec(req.getIntervalSec());
        job.setTimeoutSec(req.getTimeoutSec());
        job.setRetryTimes(req.getRetryTimes());
        job.setRetryIntervalSec(req.getRetryIntervalSec());
        job.setRouteStrategy(req.getRouteStrategy());
        job.setBlockStrategy(req.getBlockStrategy());
        job.setParams(writeParams(req.getParams()));
        job.setStatus(req.getStatus());
        job.setOwnerAccountId(req.getOwnerAccountId());
        job.setNextFireTime(computeNextFire(job));
    }

    private LocalDateTime computeNextFire(JobInfo job) {
        if (job.getStatus() == null || job.getStatus() != 1) {
            return null;
        }
        if ("CRON".equals(job.getTriggerType())) {
            return cronService.nextFireTime(job.getCronExpr(), LocalDateTime.now());
        }
        return LocalDateTime.now().plusSeconds(job.getIntervalSec());
    }

    private JobInfo requireJob(Long id) {
        JobInfo job = id == null ? null : jobInfoMapper.selectById(id);
        if (job == null) {
            throw new BizException(JobErrorCode.JOB_NOT_FOUND);
        }
        return job;
    }

    private JobInfoVO toVO(JobInfo job, Map<String, Set<String>> handlerCache) {
        JobInfoVO vo = new JobInfoVO();
        BeanUtils.copyProperties(job, vo);
        vo.setParams(readParams(job.getParams()));
        Set<String> known = handlerCache.computeIfAbsent(job.getAppCode(), registryService::knownHandlers);
        vo.setHandlerVerified(known.contains(job.getHandler()));
        return vo;
    }

    private String writeParams(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "任务参数序列化失败：" + e.getMessage());
        }
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

    private String requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, field + " 不能为空");
        }
        return value.trim();
    }
}
