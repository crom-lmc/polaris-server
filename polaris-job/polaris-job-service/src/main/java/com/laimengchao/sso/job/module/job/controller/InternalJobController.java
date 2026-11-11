package com.laimengchao.sso.job.module.job.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.job.client.dto.JobDefinitionsRequest;
import com.laimengchao.sso.job.client.dto.JobHeartbeatRequest;
import com.laimengchao.sso.job.module.job.service.JobRegistryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用侧内部端点，凭 X-Internal-Token 准入（见 InternalTokenInterceptor）。
 * 应用通过 job-client 自动上报，无需手写调用。
 */
@RestController
@RequestMapping("/internal/jobs")
public class InternalJobController {

    private final JobRegistryService registryService;

    public InternalJobController(JobRegistryService registryService) {
        this.registryService = registryService;
    }

    @PostMapping("/definitions")
    public R<Void> definitions(@RequestBody JobDefinitionsRequest request) {
        registryService.reportDefinitions(request);
        return R.ok();
    }

    @PostMapping("/ping")
    public R<Void> ping(@RequestBody JobHeartbeatRequest request) {
        registryService.ping(request);
        return R.ok();
    }

    @PostMapping("/offline")
    public R<Void> offline(@RequestBody JobHeartbeatRequest request) {
        registryService.offline(request);
        return R.ok();
    }
}
