package com.laimengchao.sso.forum.module.experience.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 经验获取规则，前后台共用 */
@Data
public class ExperienceRuleVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String ruleKey;
    private String name;
    private Integer experience;
    private Integer sortNo;
    private Boolean enabled;
}
