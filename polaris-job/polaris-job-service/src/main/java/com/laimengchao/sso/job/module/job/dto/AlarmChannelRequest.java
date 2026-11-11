package com.laimengchao.sso.job.module.job.dto;

import lombok.Data;

/**
 * 全局通知渠道开关入参。
 */
@Data
public class AlarmChannelRequest {

    /** SITE / EMAIL */
    private String channel;
    private Integer enabled;
}
