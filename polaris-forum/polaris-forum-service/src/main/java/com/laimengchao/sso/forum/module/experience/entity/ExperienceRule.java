package com.laimengchao.sso.forum.module.experience.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 经验获取规则：定义某种行为每次增加的经验值 */
@Data
public class ExperienceRule {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String ruleKey;
    private String name;
    private Integer experience;
    private Integer sortNo;
    private Boolean enabled;
    private String createdAt;
    private String updatedAt;
}
