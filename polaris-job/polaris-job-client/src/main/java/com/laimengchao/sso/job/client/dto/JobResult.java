package com.laimengchao.sso.job.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 任务执行结果。应用侧只回报成功与否和摘要，耗时由中心兜底计算。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private boolean success;
    private String message;
    private long costMs;

    public static JobResult ok() {
        return new JobResult(true, "success", 0L);
    }

    public static JobResult ok(String message) {
        return new JobResult(true, message, 0L);
    }

    public static JobResult fail(String message) {
        return new JobResult(false, message, 0L);
    }
}
