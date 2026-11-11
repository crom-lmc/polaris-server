package com.laimengchao.sso.job.client.executor;

import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.dto.JobRunRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用统一执行端点：每个应用只暴露这一个入口，内部按 handler 分发。
 * <p>
 * 由 job-client 自动装配注册，业务应用无需再写 Controller。
 * 额外加条件注解：调度中心自身扫描 com.laimengchao.sso.job 时会扫到本类，
 * 中心通过 job.executor.enabled=false 排除，避免把中心自己注册成执行器。
 */
@RestController
@ConditionalOnProperty(prefix = "job.executor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class JobExecutorController {

    private final JobDispatcher dispatcher;
    private final String token;

    public JobExecutorController(JobDispatcher dispatcher, String token) {
        this.dispatcher = dispatcher;
        this.token = token == null ? "" : token.trim();
    }

    @PostMapping("/internal/jobs/run")
    public ResponseEntity<JobResult> run(@RequestHeader(value = "X-Job-Token", required = false) String actualToken,
                                         @RequestBody JobRunRequest request) {
        if (!token.isEmpty() && !token.equals(actualToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(JobResult.fail("内部调用凭据无效"));
        }
        if (request == null || request.getHandler() == null || request.getHandler().isBlank()) {
            return ResponseEntity.badRequest().body(JobResult.fail("handler 不能为空"));
        }
        return ResponseEntity.ok(dispatcher.dispatch(request));
    }
}
