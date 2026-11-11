package com.laimengchao.sso.job.module.job.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 任务执行日志 VO */
@Data
public class JobLogVO {

    private Long id;

    private Long jobId;

    /** 任务名称（关联回填） */
    private String jobName;

    /** 应用编码 */
    private String appCode;

    /** Handler 名 */
    private String handler;

    /** SCHEDULE / MANUAL / RETRY */
    private String triggerType;

    private LocalDateTime plannedAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /** 0=执行中 1=成功 2=失败 3=超时 4=取消 */
    private Integer status;

    /** 耗时毫秒 */
    private Integer durationMs;

    /** 重试序号，0 为首次 */
    private Integer retryIndex;

    /** 实际执行实例 */
    private String executorAddress;

    /** 异常摘要 */
    private String errorMsg;

    /** 输出尾部（截断） */
    private String output;
}
