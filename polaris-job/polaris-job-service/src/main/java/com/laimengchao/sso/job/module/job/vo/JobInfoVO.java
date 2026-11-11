package com.laimengchao.sso.job.module.job.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 任务视图对象。params 以键值对象给前端，前端按表格维护。 */
@Data
public class JobInfoVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String appCode;
    private String name;
    private String handler;
    private String description;
    private String triggerType;
    private String cronExpr;
    private Integer intervalSec;
    private Integer timeoutSec;
    private Integer retryTimes;
    private Integer retryIntervalSec;
    private String routeStrategy;
    private String blockStrategy;
    private Map<String, String> params;
    private Integer status;
    private LocalDateTime nextFireTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerAccountId;

    /** 应用已上报清单、且包含该 handler 时为 true；应用从未上报时为 false（无法校验） */
    private Boolean handlerVerified;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
