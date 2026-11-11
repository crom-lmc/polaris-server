package com.laimengchao.sso.job.common;

import com.laimengchao.sso.common.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 调度中心业务错误码段（9001 起）。实现 IErrorCode 即可接入统一的
 * R / BizException / GlobalExceptionHandler。
 */
@Getter
@AllArgsConstructor
public enum JobErrorCode implements IErrorCode {

    JOB_NOT_FOUND(9001, "任务不存在"),
    JOB_DUPLICATED(9002, "该应用下已存在相同 Handler 的任务"),
    JOB_CRON_INVALID(9003, "CRON 表达式不合法"),
    JOB_HANDLER_UNKNOWN(9004, "Handler 未在应用上报的清单中"),
    JOB_TRIGGER_INVALID(9005, "触发配置不合法"),
    JOB_EXEC_CONFIG_INVALID(9006, "执行配置不合法"),
    JOB_REGISTRY_NOT_FOUND(9007, "执行器未注册");

    private final int code;
    private final String message;
}
