package com.laimengchao.sso.openapi.module.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 接口调用日志实体（进出统一，按 direction 区分）。 */
@Data
@TableName("api_call_log")
public class ApiCallLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String traceId;
    private String direction;
    private String appCode;
    private String apiCode;
    private String channel;
    private String httpMethod;
    private String requestPath;
    private String reqMasked;
    private String respMasked;
    private Integer httpStatus;
    private String bizCode;
    private Integer costMs;
    private String clientIp;
    private String errorMsg;
    private LocalDateTime createdAt;
}
