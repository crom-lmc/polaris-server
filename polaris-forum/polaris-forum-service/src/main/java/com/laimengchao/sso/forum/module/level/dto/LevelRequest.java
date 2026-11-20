package com.laimengchao.sso.forum.module.level.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class LevelRequest {

    private Object levelNo;
    private Object name;
    private Object minExperience;
    private Object color;
    private Object icon;
    private Object description;
    private Object privileges;
    private Object sortNo;
    private Object enabled;

    @JsonIgnore private boolean levelNoPresent;
    @JsonIgnore private boolean namePresent;
    @JsonIgnore private boolean minExperiencePresent;
    @JsonIgnore private boolean colorPresent;
    @JsonIgnore private boolean iconPresent;
    @JsonIgnore private boolean descriptionPresent;
    @JsonIgnore private boolean privilegesPresent;
    @JsonIgnore private boolean sortNoPresent;
    @JsonIgnore private boolean enabledPresent;

    public void setLevelNo(Object levelNo) { this.levelNo = levelNo; this.levelNoPresent = true; }
    public void setName(Object name) { this.name = name; this.namePresent = true; }
    public void setMinExperience(Object minExperience) { this.minExperience = minExperience; this.minExperiencePresent = true; }
    public void setColor(Object color) { this.color = color; this.colorPresent = true; }
    public void setIcon(Object icon) { this.icon = icon; this.iconPresent = true; }
    public void setDescription(Object description) { this.description = description; this.descriptionPresent = true; }
    public void setPrivileges(Object privileges) { this.privileges = privileges; this.privilegesPresent = true; }
    public void setSortNo(Object sortNo) { this.sortNo = sortNo; this.sortNoPresent = true; }
    public void setEnabled(Object enabled) { this.enabled = enabled; this.enabledPresent = true; }
}
