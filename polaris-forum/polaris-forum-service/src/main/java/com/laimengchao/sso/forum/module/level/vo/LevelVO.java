package com.laimengchao.sso.forum.module.level.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 社区等级定义，前台晋升路线与后台配置共用 */
@Data
public class LevelVO {

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
    /** 系统内置等级，不允许删除（名称/门槛/权益仍可编辑） */
    private Boolean builtin;
}
