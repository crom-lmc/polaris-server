package com.laimengchao.sso.forum.module.badge.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class BadgeVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    private String description;
    private String icon;
    private String color;
    private Integer sortNo;
    private String ruleType;
    private Integer threshold;
    private String status;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;
    private String publishedAt;
    private String createdAt;
    private String updatedAt;
}
