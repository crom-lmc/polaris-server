package com.laimengchao.sso.job.client.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 中心调用应用统一执行端点的请求体。
 * <p>
 * 任务唯一标识是 {@code appCode + handler}；中心不感知 Java 接口名与方法名。
 */
@Data
public class JobRunRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 应用编码，如 ehr / settings / cms */
    private String appCode;

    /** 应用内 JobHandler#name() */
    private String handler;

    /** 本次执行日志 ID，用于应用侧回传与排查 */
    private Long logId;

    /** 计划触发时间 */
    private LocalDateTime fireTime;

    /** 任务参数（字符串键值对），无参数时为空 Map */
    private Map<String, String> params;
}
