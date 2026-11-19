package com.laimengchao.sso.forum.module.taxonomy.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

@Data
public class TagUpdateRequest {

    private Object sortNo;
    private Object enabled;

    @JsonIgnore
    private boolean sortNoPresent;
    @JsonIgnore
    private boolean enabledPresent;
    @JsonIgnore
    private Set<String> unsupportedFields = new HashSet<>();

    public void setSortNo(Object sortNo) {
        this.sortNo = sortNo;
        this.sortNoPresent = true;
    }

    public void setEnabled(Object enabled) {
        this.enabled = enabled;
        this.enabledPresent = true;
    }

    @JsonAnySetter
    public void unsupportedField(String name, Object value) {
        unsupportedFields.add(name);
    }
}
