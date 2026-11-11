package com.laimengchao.sso.job.module.job.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/** 任务新建/更新请求。任务唯一标识 = appCode + handler。 */
@Data
public class JobSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appCode;
    private String name;
    private String handler;
    private String description;
    /** CRON / FIXED_RATE / FIXED_DELAY */
    private String triggerType;
    private String cronExpr;
    private Integer intervalSec;
    private Integer timeoutSec;
    private Integer retryTimes;
    private Integer retryIntervalSec;
    private String routeStrategy;
    private String blockStrategy;
    private Map<String, String> params;
    /** 0 暂停 / 1 运行；不传默认 0 */
    private Integer status;
    private Long ownerAccountId;
}
