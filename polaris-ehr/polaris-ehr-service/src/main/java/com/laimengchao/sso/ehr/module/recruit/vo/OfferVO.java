package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

/** Offer 列表项 */
@Data
public class OfferVO {

    private String id;
    /** 单据编号：撤回/驳回后重新发起会沿用同一个单号 */
    private String offerNo;
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
    /** HR 线下确认/放弃时间与操作人 */
    private String confirmedAt;
    private String confirmedBy;
    private String confirmedByName;
    private String jobId;
    /** 办理入职后生成的待入职档案 */
    private String employeeId;
    private String employeeName;
    private String employeeStatus;
    private String createdAt;
}
