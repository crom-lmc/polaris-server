package com.laimengchao.sso.job.module.job.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报警流水展示对象：带任务名与应用编码，方便前端直接展示。
 */
@Data
public class AlarmRecordVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long jobId;
    private String jobName;
    private String appCode;
    /** FAIL / RECOVER */
    private String event;
    private String channel;
    private String receivers;
    private String content;
    /** 1 成功 / 2 失败 */
    private Integer status;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
