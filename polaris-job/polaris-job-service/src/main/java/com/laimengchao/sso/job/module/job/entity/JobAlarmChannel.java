package com.laimengchao.sso.job.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报警全局通知渠道开关。固定两条：SITE（站内消息）、EMAIL（邮件）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_alarm_channel")
public class JobAlarmChannel extends BaseEntity {

    /** SITE / EMAIL */
    private String channel;
    /** 0 停用 / 1 启用 */
    private Integer enabled;
}
