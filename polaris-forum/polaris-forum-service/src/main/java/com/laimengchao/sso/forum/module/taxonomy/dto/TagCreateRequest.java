package com.laimengchao.sso.forum.module.taxonomy.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class TagCreateRequest {

    private Object board;
    private Object boardKey;
    private Object name;
    private Object sortNo;
    private Object enabled;

    @JsonIgnore
    private boolean enabledPresent;

    public void setEnabled(Object enabled) {
        this.enabled = enabled;
        this.enabledPresent = true;
    }
}
