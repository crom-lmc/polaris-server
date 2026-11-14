package com.laimengchao.sso.ehr.module.recruit.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.ehr.module.recruit.dto.JobPostRequest;
import com.laimengchao.sso.ehr.module.recruit.service.JobService;
import com.laimengchao.sso.ehr.module.recruit.vo.JobPostVO;
import com.laimengchao.sso.ehr.security.Permit;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 招聘职位后台维护：发布后门户 /careers 可见 */
@RestController
@RequestMapping("/api/ehr/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping
    @Permit(PermissionCodes.RECRUIT_VIEW)
    public R<R.Page<JobPostVO>> page(@RequestParam(required = false) String status,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return R.ok(jobService.page(status, keyword, page, Math.min(size, 200)));
    }

    @GetMapping("/{id}")
    @Permit(PermissionCodes.RECRUIT_VIEW)
    public R<JobPostVO> detail(@PathVariable Long id) {
        return R.ok(jobService.detail(id));
    }

    @PostMapping
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<JobPostVO> create(@Valid @RequestBody JobPostRequest req) {
        return R.ok(jobService.create(req));
    }

    @PutMapping("/{id}")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<JobPostVO> update(@PathVariable Long id, @Valid @RequestBody JobPostRequest req) {
        return R.ok(jobService.update(id, req));
    }

    @PostMapping("/{id}/publish")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<Void> publish(@PathVariable Long id) {
        jobService.publish(id);
        return R.ok();
    }

    @PostMapping("/{id}/close")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<Void> close(@PathVariable Long id) {
        jobService.close(id);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Permit(PermissionCodes.RECRUIT_MANAGE)
    public R<Void> delete(@PathVariable Long id) {
        jobService.delete(id);
        return R.ok();
    }
}
