package com.laimengchao.sso.forum.module.forum.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class ForumPointLog {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private Integer changePoints;
    private Integer balance;
    private String bizType;
    private String bizId;
    private String remark;
    private String createdAt;
}
