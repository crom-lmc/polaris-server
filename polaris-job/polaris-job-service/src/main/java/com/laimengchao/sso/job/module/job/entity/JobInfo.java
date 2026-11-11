package com.laimengchao.sso.job.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务定义。唯一标识是 {@code app_code + handler}，不保存 Feign 接口名/方法名。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_info")
public class JobInfo extends BaseEntity {

    private String appCode;
    private String name;
    /** 应用内 JobHandler#name() */
    private String handler;
    private String description;
    /** CRON / FIXED_RATE / FIXED_DELAY */
    private String triggerType;
    private String cronExpr;
    private Integer intervalSec;
    private Integer timeoutSec;
    private Integer retryTimes;
    private Integer retryIntervalSec;
    /** ROUND / FAILOVER / RANDOM / FIRST / BROADCAST */
    private String routeStrategy;
    /** DISCARD / SERIAL / COVER */
    private String blockStrategy;
    /** 参数 JSON 对象，空为 {} */
    private String params;
    /** 0 暂停 / 1 运行 */
    private Integer status;
    private LocalDateTime nextFireTime;
    private Long ownerAccountId;
    private Integer lockVersion;
}
