package com.laimengchao.sso.job.client.handler;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/** 执行上下文：中心只传触发信息与参数，不介入业务判断。 */
@Data
@AllArgsConstructor
public class JobContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private String appCode;
    private Long logId;
    private LocalDateTime fireTime;
    private Map<String, String> params;

    public String param(String key) {
        return params == null ? null : params.get(key);
    }

    public String param(String key, String defaultValue) {
        String value = param(key);
        return value == null ? defaultValue : value;
    }
}
