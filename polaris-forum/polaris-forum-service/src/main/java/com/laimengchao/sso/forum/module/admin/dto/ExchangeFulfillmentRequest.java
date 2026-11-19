package com.laimengchao.sso.forum.module.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ExchangeFulfillmentRequest {

    private Object trackingNo;
    private Object remark;
    private Object issueRemark;

    @JsonIgnore
    private boolean remarkPresent;
    @JsonIgnore
    private boolean issueRemarkPresent;

    public void setRemark(Object remark) {
        this.remark = remark;
        this.remarkPresent = true;
    }

    public void setIssueRemark(Object issueRemark) {
        this.issueRemark = issueRemark;
        this.issueRemarkPresent = true;
    }
}
