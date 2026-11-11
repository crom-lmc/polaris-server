package com.laimengchao.sso.job.client.executor;

import com.laimengchao.sso.job.client.dto.JobResult;
import com.laimengchao.sso.job.client.dto.JobRunRequest;
import com.laimengchao.sso.job.client.handler.JobContext;
import com.laimengchao.sso.job.client.handler.JobHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用内分发器：按 {@link JobHandler#name()} 找到 Bean 并执行。
 * <p>
 * 同名 Handler 直接启动失败——配置里只能写一个名字，重复注册必然有一个永远不生效，
 * 与其运行期踩坑，不如启动时就暴露。
 */
@Slf4j
public class JobDispatcher {

    private final Map<String, JobHandler> handlers;

    public JobDispatcher(List<JobHandler> handlerList) {
        Map<String, JobHandler> map = new LinkedHashMap<>();
        if (handlerList != null) {
            for (JobHandler handler : handlerList) {
                String name = handler.name();
                if (name == null || name.isBlank()) {
                    log.warn("忽略 name() 为空的 JobHandler：{}", handler.getClass().getName());
                    continue;
                }
                JobHandler previous = map.putIfAbsent(name.trim(), handler);
                if (previous != null) {
                    throw new IllegalStateException("JobHandler 名称重复：" + name
                            + "（" + previous.getClass().getName() + " / " + handler.getClass().getName() + "）");
                }
            }
        }
        this.handlers = Map.copyOf(map);
    }

    /** 已注册的 Handler 名称（上报中心用） */
    public List<String> names() {
        return handlers.keySet().stream().sorted().toList();
    }

    public JobResult dispatch(JobRunRequest request) {
        if (request == null || request.getHandler() == null || request.getHandler().isBlank()) {
            return JobResult.fail("handler 不能为空");
        }
        JobHandler handler = handlers.get(request.getHandler().trim());
        if (handler == null) {
            return JobResult.fail("Handler 不存在：" + request.getHandler());
        }
        long start = System.currentTimeMillis();
        try {
            JobContext ctx = new JobContext(
                    request.getAppCode(),
                    request.getLogId(),
                    request.getFireTime(),
                    request.getParams() == null ? Map.of() : request.getParams());
            JobResult result = handler.execute(ctx);
            if (result == null) {
                result = JobResult.ok();
            }
            result.setCostMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("JobHandler 执行失败：handler={} logId={}", request.getHandler(), request.getLogId(), e);
            JobResult result = JobResult.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            result.setCostMs(System.currentTimeMillis() - start);
            return result;
        }
    }
}
