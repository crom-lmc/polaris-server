package com.laimengchao.sso.forum.module.report.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ReportVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long reporterAccountId;
    private String reporterName;
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long threadId;
    private String reason;
    private String detail;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long handlerAccountId;
    private String handlerName;
    private String handleResult;
    private String handledAt;
    private String createdAt;
    private String threadTitle;
    private String targetContent;
}
