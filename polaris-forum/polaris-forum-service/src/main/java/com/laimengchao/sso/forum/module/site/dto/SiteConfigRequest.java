package com.laimengchao.sso.forum.module.site.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class SiteConfigRequest {

    private Object aboutContent;
    private Object footerText;

    @JsonIgnore
    private boolean aboutContentPresent;
    @JsonIgnore
    private boolean footerTextPresent;

    public void setAboutContent(Object aboutContent) {
        this.aboutContent = aboutContent;
        this.aboutContentPresent = true;
    }

    public void setFooterText(Object footerText) {
        this.footerText = footerText;
        this.footerTextPresent = true;
    }
}
