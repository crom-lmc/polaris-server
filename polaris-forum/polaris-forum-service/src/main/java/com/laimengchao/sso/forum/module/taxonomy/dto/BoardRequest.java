package com.laimengchao.sso.forum.module.taxonomy.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class BoardRequest {

    private Object key;
    private Object parentKey;
    private Object name;
    private Object description;
    private Object color;
    private Object sortNo;
    private Object enabled;

    @JsonIgnore
    private boolean parentKeyPresent;
    @JsonIgnore
    private boolean namePresent;
    @JsonIgnore
    private boolean descriptionPresent;
    @JsonIgnore
    private boolean colorPresent;
    @JsonIgnore
    private boolean sortNoPresent;
    @JsonIgnore
    private boolean enabledPresent;

    public void setParentKey(Object parentKey) {
        this.parentKey = parentKey;
        this.parentKeyPresent = true;
    }

    public void setName(Object name) {
        this.name = name;
        this.namePresent = true;
    }

    public void setDescription(Object description) {
        this.description = description;
        this.descriptionPresent = true;
    }

    public void setColor(Object color) {
        this.color = color;
        this.colorPresent = true;
    }

    public void setSortNo(Object sortNo) {
        this.sortNo = sortNo;
        this.sortNoPresent = true;
    }

    public void setEnabled(Object enabled) {
        this.enabled = enabled;
        this.enabledPresent = true;
    }
}
