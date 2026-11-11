package com.laimengchao.sso.job.module.job.dto;

import lombok.Data;

/**
 * 报警规则保存入参。
 */
@Data
public class AlarmRuleRequest {

    private String name;
    /** JOB / APP / ALL */
    private String scopeType;
    /** 任务ID或应用编码 */
    private String scopeValue;
    /** FAIL / TIMEOUT / CONSECUTIVE_FAIL */
    private String triggerCondition;
    private Integer threshold;
    /** SITE,EMAIL */
    private String channels;
    private String accountIds;
    private Integer silenceMinutes;
    /** 0 停用 / 1 启用 */
    private Integer status;
}
