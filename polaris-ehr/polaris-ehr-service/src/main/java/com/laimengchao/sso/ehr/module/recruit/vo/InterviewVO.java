package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

/** 面试记录（HR 看板 / 面试官待办共用），ID 以字符串下发 */
@Data
public class InterviewVO {

    private String id;
    private String candidateId;
    private String candidateName;
    private String jobId;
    /** 面试单号：IV-yyyyMMdd-6位流水 */
    private String interviewNo;
    private Integer roundNo;
    private String interviewerId;
    private String interviewerName;
    private String scheduledAt;
    /** ONSITE / VIDEO / PHONE */
    private String method;
    private String location;
    /** SCHEDULED / DONE / CANCELED */
    private String status;
    private Integer score;
    private String feedback;
    private String feedbackAt;
    /** PASS / FAIL / null（未判定） */
    private String roundResult;
    private String decidedAt;
    private String remark;
}
