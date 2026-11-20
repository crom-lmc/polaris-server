package com.laimengchao.sso.forum.module.experience.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ExperienceRuleRequest {

    private Object ruleKey;
    private Object name;
    private Object experience;
    private Object sortNo;
    private Object enabled;

    @JsonIgnore private boolean ruleKeyPresent;
    @JsonIgnore private boolean namePresent;
    @JsonIgnore private boolean experiencePresent;
    @JsonIgnore private boolean sortNoPresent;
    @JsonIgnore private boolean enabledPresent;

    public void setRuleKey(Object ruleKey) { this.ruleKey = ruleKey; this.ruleKeyPresent = true; }
    public void setName(Object name) { this.name = name; this.namePresent = true; }
    public void setExperience(Object experience) { this.experience = experience; this.experiencePresent = true; }
    public void setSortNo(Object sortNo) { this.sortNo = sortNo; this.sortNoPresent = true; }
    public void setEnabled(Object enabled) { this.enabled = enabled; this.enabledPresent = true; }
}
