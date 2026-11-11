package com.laimengchao.sso.job.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.entity.JobLog;
import com.laimengchao.sso.job.module.job.mapper.JobInfoMapper;
import com.laimengchao.sso.job.module.job.mapper.JobLogMapper;
import com.laimengchao.sso.job.module.job.vo.JobLogVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 任务执行日志查询服务 */
@Service
public class JobLogService {

    private final JobLogMapper jobLogMapper;
    private final JobInfoMapper jobInfoMapper;

    public JobLogService(JobLogMapper jobLogMapper, JobInfoMapper jobInfoMapper) {
        this.jobLogMapper = jobLogMapper;
        this.jobInfoMapper = jobInfoMapper;
    }

    public PageResult<JobLogVO> list(long pageNo, long size, Long jobId, String appCode,
                                     Integer status, LocalDateTime startTime, LocalDateTime endTime) {
        long page = Math.max(1, pageNo);
        long pageSize = Math.min(200, Math.max(1, size));

        LambdaQueryWrapper<JobLog> qw = new LambdaQueryWrapper<>();
        qw.eq(jobId != null, JobLog::getJobId, jobId);
        qw.eq(status != null, JobLog::getStatus, status);
        qw.ge(startTime != null, JobLog::getStartedAt, startTime);
        qw.le(endTime != null, JobLog::getStartedAt, endTime);

        Page<JobLog> result = jobLogMapper.selectPage(new Page<>(page, pageSize), qw);

        // 关联回填任务名 / appCode / handler（尽量走索引 + 单次批量查）
        Map<Long, JobInfo> cache = new HashMap<>();
        List<JobLogVO> records = result.getRecords().stream()
                .map(log -> toVO(log, cache, appCode))
                .toList();

        // 如果传了 appCode 过滤，但日志本身没存 appCode，靠关联筛掉
        if (StringUtils.hasText(appCode)) {
            records = records.stream()
                    .filter(vo -> appCode.trim().equalsIgnoreCase(vo.getAppCode()))
                    .toList();
        }

        return PageResult.of(records, result.getTotal(), page, pageSize);
    }

    public JobLogVO get(Long id) {
        JobLog log = id == null ? null : jobLogMapper.selectById(id);
        if (log == null) {
            return null;
        }
        return toVO(log, new HashMap<>(), null);
    }

    private JobLogVO toVO(JobLog log, Map<Long, JobInfo> cache, String appCodeHint) {
        JobLogVO vo = new JobLogVO();
        BeanUtils.copyProperties(log, vo);
        JobInfo job = cache.computeIfAbsent(log.getJobId(),
                id -> jobInfoMapper.selectById(id));
        if (job != null) {
            vo.setJobName(job.getName());
            vo.setAppCode(job.getAppCode());
            vo.setHandler(job.getHandler());
        } else if (appCodeHint != null) {
            vo.setAppCode(appCodeHint);
        }
        return vo;
    }
}
