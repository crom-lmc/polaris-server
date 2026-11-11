package com.laimengchao.sso.job.client.handler;

import com.laimengchao.sso.job.client.dto.JobResult;

/**
 * 应用侧任务契约：一个任务一个 Bean，{@link #name()} 即调度中心配置里的 handler。
 * <p>
 * 原任务类不需要改业务代码，只需实现本接口并把方法体包进 {@link #execute(JobContext)}。
 * 参数差异通过 ctx.params 传递，不要用方法重载（按 Bean 名查找时重载无法区分）。
 */
public interface JobHandler {

    /** 与 job_info.handler 一致，应用内唯一 */
    String name();

    JobResult execute(JobContext ctx) throws Exception;
}
