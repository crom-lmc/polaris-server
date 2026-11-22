package com.laimengchao.sso.openapi.module.endpoint.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/** 开放 API 目录实体。 */
@Data
@TableName("api_open_endpoint")
public class ApiOpenEndpoint {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String apiCode;
    private String apiName;
    private Integer direction;
    /** 操作类型：QUERY/CREATE/UPDATE/DELETE/EXECUTE（出站调用），由管理端注册时指定 */
    private String operationType;
    private String appCode;
    private String method;
    private String path;
    private String targetUrl;
    private String authType;
    private String requiredPerm;
    private Integer rateLimitPerMin;
    private Integer timeoutMs;
    private Integer retryMax;
    private Integer idempotent;
    private String maskRules;
    private String remark;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;
}
