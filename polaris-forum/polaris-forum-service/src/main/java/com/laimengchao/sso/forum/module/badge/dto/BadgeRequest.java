package com.laimengchao.sso.forum.module.badge.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class BadgeRequest {

    private Object name;
    private Object description;
    private Object icon;
    private Object color;
    private Object sortNo;
    private Object ruleType;
    private Object threshold;

    @JsonIgnore
    private boolean namePresent;
    @JsonIgnore
    private boolean descriptionPresent;
    @JsonIgnore
    private boolean iconPresent;
    @JsonIgnore
    private boolean colorPresent;
    @JsonIgnore
    private boolean sortNoPresent;
    @JsonIgnore
    private boolean ruleTypePresent;
    @JsonIgnore
    private boolean thresholdPresent;

    public void setName(Object name) {
        this.name = name;
        this.namePresent = true;
    }

    public void setDescription(Object description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public void setIcon(Object icon) {
        this.icon = icon;
        this.iconPresent = true;
    }

    public void setColor(Object color) {
        this.color = color;
        this.colorPresent = true;
    }

    public void setSortNo(Object sortNo) {
        this.sortNo = sortNo;
        this.sortNoPresent = true;
    }

    public void setRuleType(Object ruleType) {
        this.ruleType = ruleType;
        this.ruleTypePresent = true;
    }

    public void setThreshold(Object threshold) {
        this.threshold = threshold;
        this.thresholdPresent = true;
    }
}
