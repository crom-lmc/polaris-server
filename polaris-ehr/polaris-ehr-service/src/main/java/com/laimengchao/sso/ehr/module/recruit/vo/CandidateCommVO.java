package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

/** 候选人沟通记录 */
@Data
public class CandidateCommVO {

    private String id;
    private String candidateId;
    /** OFFER_TALK / PHONE / OTHER */
    private String type;
    private String content;
    private String expectedSalary;
    private String occurredAt;
    private String operatorName;
    private String createdAt;
}
