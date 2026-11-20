package com.laimengchao.sso.forum.module.banner.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class BannerRequest {

    private Object title;
    private Object imageAttachmentId;
    private Object linkUrl;
    private Object sortNo;
    private Object enabled;

    @JsonIgnore
    private boolean titlePresent;
    @JsonIgnore
    private boolean imageAttachmentIdPresent;
    @JsonIgnore
    private boolean linkUrlPresent;
    @JsonIgnore
    private boolean sortNoPresent;
    @JsonIgnore
    private boolean enabledPresent;

    public void setTitle(Object title) {
        this.title = title;
        this.titlePresent = true;
    }

    public void setImageAttachmentId(Object imageAttachmentId) {
        this.imageAttachmentId = imageAttachmentId;
        this.imageAttachmentIdPresent = true;
    }

    public void setLinkUrl(Object linkUrl) {
        this.linkUrl = linkUrl;
        this.linkUrlPresent = true;
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
