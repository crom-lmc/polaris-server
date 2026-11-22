package com.laimengchao.sso.openapi.module.log.vo;

import lombok.Data;

/** 调用日志查询条件。 */
@Data
public class LogQuery {
    /** 方向：INBOUND / OUTBOUND，空=全部 */
    private String direction;
    private String appCode;
    private String apiCode;
    private String channel;
    /** 起始时间 yyyy-MM-dd HH:mm:ss */
    private String from;
    private String to;
    /** 只看失败（http_status>=400 或 biz_code!=0） */
    private Boolean onlyFailed;
    private int page = 1;
    private int size = 20;
}
