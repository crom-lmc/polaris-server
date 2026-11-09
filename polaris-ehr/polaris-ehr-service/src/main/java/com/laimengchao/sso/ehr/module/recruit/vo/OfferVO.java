package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

/** Offer 列表项 */
@Data
public class OfferVO {

    private String id;
    private String candidateId;
    private String candidateName;
    private String candidateMobile;
    private String position;
    private String salary;
    private String expectedOnboardDate;
    private String orgId;
    private String orgPath;
    private String status;
    private String remark;
    /** 审批通过后生成的待入职档案 */
    private String employeeId;
    private String employeeName;
    private String employeeStatus;
    private String createdAt;
}
