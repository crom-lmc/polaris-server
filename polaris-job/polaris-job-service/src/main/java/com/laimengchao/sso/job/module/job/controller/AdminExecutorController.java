package com.laimengchao.sso.job.module.job.controller;

import com.laimengchao.sso.common.R;
import com.laimengchao.sso.job.constant.PermissionCodes;
import com.laimengchao.sso.job.module.job.service.JobRegistryService;
import com.laimengchao.sso.job.module.job.vo.ExecutorVO;
import com.laimengchao.sso.job.security.Permit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 执行器列表（在线状态按 90 秒心跳窗口实时计算） */
@RestController
@RequestMapping("/api/jobs/executors")
public class AdminExecutorController {

    private final JobRegistryService registryService;

    public AdminExecutorController(JobRegistryService registryService) {
        this.registryService = registryService;
    }

    @GetMapping
    @Permit(PermissionCodes.JOB_VIEW)
    public R<List<ExecutorVO>> list(@RequestParam(required = false) String appCode,
                                    @RequestParam(required = false) Boolean online) {
        return R.ok(registryService.list(appCode, online));
    }
}
