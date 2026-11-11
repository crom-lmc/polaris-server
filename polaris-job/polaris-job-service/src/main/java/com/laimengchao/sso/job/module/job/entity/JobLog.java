package com.laimengchao.sso.job.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务执行日志。J1 只建表，J3 接入写入与查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_log")
public class JobLog extends BaseEntity {

    private Long jobId;
    /** SCHEDULE / MANUAL / RETRY */
    private String triggerType;
    private LocalDateTime plannedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    /** 0 执行中 / 1 成功 / 2 失败 / 3 超时 / 4 取消 */
    private Integer status;
    private Integer durationMs;
    private Integer retryIndex;
    private String executorAddress;
    private String errorMsg;
    private String output;
}
