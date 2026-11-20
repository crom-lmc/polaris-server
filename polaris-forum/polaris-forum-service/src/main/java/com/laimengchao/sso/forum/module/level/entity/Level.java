package com.laimengchao.sso.forum.module.level.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

@Data
public class Level {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Integer levelNo;
    private String name;
    private Integer minExperience;
    private String color;
    private String icon;
    private String description;
    private String privileges;
    private Integer sortNo;
    private Boolean enabled;
    private Boolean builtin;
    private String createdAt;
    private String updatedAt;
}
