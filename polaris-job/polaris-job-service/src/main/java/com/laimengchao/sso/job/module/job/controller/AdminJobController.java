package com.laimengchao.sso.job.module.job.controller;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.PageResult;
import com.laimengchao.sso.common.R;
import com.laimengchao.sso.job.common.JobErrorCode;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.job.module.job.dto.JobSaveRequest;
import com.laimengchao.sso.job.module.job.entity.JobInfo;
import com.laimengchao.sso.job.module.job.service.CronService;
import com.laimengchao.sso.job.module.job.service.JobDispatchService;
import com.laimengchao.sso.job.module.job.service.JobInfoService;
import com.laimengchao.sso.job.module.job.vo.JobInfoVO;
import com.laimengchao.sso.job.security.Permit;
import lombok.Data;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 任务管理（网关后，凭 job:view / job:manage 权限） */
@RestController
@RequestMapping("/api/jobs")
public class AdminJobController {

    private static final DateTimeFormatter PREVIEW_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JobInfoService jobInfoService;
    private final CronService cronService;
    private final JobDispatchService dispatchService;

    public AdminJobController(JobInfoService jobInfoService, CronService cronService,
                              JobDispatchService dispatchService) {
        this.jobInfoService = jobInfoService;
        this.cronService = cronService;
        this.dispatchService = dispatchService;
    }

    @GetMapping
    @Permit(PermissionCodes.JOB_VIEW)
    public R<PageResult<JobInfoVO>> list(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String appCode,
                                         @RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String triggerType) {
        return R.ok(jobInfoService.list(page, size, keyword, appCode, status, triggerType));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.JOB_VIEW)
    public R<JobInfoVO> get(@PathVariable Long id) {
        return R.ok(jobInfoService.get(id));
    }

    @PostMapping
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<JobInfoVO> create(@RequestBody JobSaveRequest request) {
        return R.ok(jobInfoService.create(request));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<JobInfoVO> update(@PathVariable Long id, @RequestBody JobSaveRequest request) {
        return R.ok(jobInfoService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        jobInfoService.delete(id);
        return R.ok();
    }

    @PatchMapping("/{id}/status")
    @Permit(PermissionCodes.JOB_MANAGE)
    public R<JobInfoVO> toggleStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return R.ok(jobInfoService.toggleStatus(id, request.getStatus()));
    }

    /** 编辑器即时校验：非法表达式返回 9003，合法返回未来 3 次执行时间 */
    @PostMapping("/cron/preview")
    @Permit(PermissionCodes.JOB_VIEW)
    public R<List<String>> previewCron(@RequestBody CronPreviewRequest request) {
        List<String> times = cronService.preview(request.getCron()).stream()
                .map(PREVIEW_FORMAT::format)
                .toList();
        return R.ok(times);
    }

    /** 手动触发一次：同步派发，立即入 job_log 并尝试执行；不影响调度下次触发时间 */
    @PostMapping("/{id}/run")
    @Permit(PermissionCodes.JOB_RUN)
    public R<String> run(@PathVariable Long id) {
        JobInfo job = dispatchService.getJob(id);
        if (job == null) {
            throw new BizException(JobErrorCode.JOB_NOT_FOUND);
        }
        if (dispatchService.isRunning(job)) {
            throw new BizException(ErrorCode.BIZ_ERROR, "任务正在执行中，请稍后再试");
        }
        LocalDateTime plannedAt = LocalDateTime.now();
        dispatchService.run(job, plannedAt);
        return R.ok("已触发，jobId=" + job.getId() + " handler=" + job.getHandler());
    }

    @Data
    public static class StatusRequest {
        private Integer status;
    }

    @Data
    public static class CronPreviewRequest {
        private String cron;
    }
}
