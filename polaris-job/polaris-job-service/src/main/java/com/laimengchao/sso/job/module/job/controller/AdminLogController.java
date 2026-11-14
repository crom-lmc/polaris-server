package com.laimengchao.sso.job.module.job.controller;

import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.job.module.job.service.JobLogService;
import com.laimengchao.sso.job.module.job.vo.JobLogVO;
import com.laimengchao.sso.job.security.Permit;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 任务执行日志（网关后，凭 job:view 权限） */
@RestController
@RequestMapping("/api/jobs/logs")
public class AdminLogController {

    private final JobLogService jobLogService;

    public AdminLogController(JobLogService jobLogService) {
        this.jobLogService = jobLogService;
    }

    @GetMapping
    @Permit(PermissionCodes.JOB_VIEW)
    public R<PageResult<JobLogVO>> list(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "20") long size,
                                        @RequestParam(required = false) Long jobId,
                                        @RequestParam(required = false) String appCode,
                                        @RequestParam(required = false) Integer status,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return R.ok(jobLogService.list(page, size, jobId, appCode, status, startTime, endTime));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.JOB_VIEW)
    public R<JobLogVO> get(@PathVariable Long id) {
        return R.ok(jobLogService.get(id));
    }
}
