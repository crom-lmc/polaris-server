package com.laimengchao.sso.job.module.job.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.job.client.dto.HandlerDefinition;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 执行器视图对象，online 由 lastHeartbeat 实时计算。 */
@Data
public class ExecutorVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String appCode;
    private String address;
    private List<HandlerDefinition> handlers;
    private String version;
    private Integer jobCount;
    private Boolean online;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
