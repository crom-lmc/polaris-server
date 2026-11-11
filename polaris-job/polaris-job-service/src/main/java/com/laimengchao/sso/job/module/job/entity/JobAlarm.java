package com.laimengchao.sso.job.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报警规则。列名 trigger_condition 避开 MySQL 保留字 condition。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_alarm")
public class JobAlarm extends BaseEntity {

    /** 规则名称 */
    private String name;
    /** JOB / APP / ALL */
    private String scopeType;
    /** 任务ID或应用编码 */
    private String scopeValue;
    /** FAIL / TIMEOUT / CONSECUTIVE_FAIL */
    private String triggerCondition;
    private Integer threshold;
    /** 通知渠道，逗号分隔 */
    private String channels;
    private String orgIds;
    private String accountIds;
    private Integer silenceMinutes;
    /** 0 停用 / 1 启用 */
    private Integer status;
}
