package com.laimengchao.sso.job.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 报警流水：静默期去重与恢复通知的依据。J3 接入写入。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_alarm_log")
public class JobAlarmLog extends BaseEntity {

    private Long jobId;
    private Long logId;
    private Long ruleId;
    /** FAIL / RECOVER */
    private String event;
    private String channel;
    private String receivers;
    /** 报警内容快照 */
    private String content;
    /** 0 待发 / 1 成功 / 2 失败 */
    private Integer status;
    private LocalDateTime sentAt;
}
