package com.laimengchao.sso.forum.module.site.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class FriendLinkRequest {

    private Object name;
    private Object url;
    private Object sortNo;
    private Object enabled;

    @JsonIgnore
    private boolean namePresent;
    @JsonIgnore
    private boolean urlPresent;
    @JsonIgnore
    private boolean sortNoPresent;
    @JsonIgnore
    private boolean enabledPresent;

    public void setName(Object name) {
        this.name = name;
        this.namePresent = true;
    }

    public void setUrl(Object url) {
        this.url = url;
        this.urlPresent = true;
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
