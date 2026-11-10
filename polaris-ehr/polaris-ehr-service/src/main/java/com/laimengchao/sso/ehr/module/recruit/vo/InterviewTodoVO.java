package com.laimengchao.sso.ehr.module.recruit.vo;

import lombok.Data;

/** 面试官「我的面试」待办项 */
@Data
public class InterviewTodoVO {

    private String id;
    private String candidateId;
    private String candidateName;
    private String position;
    private Integer roundNo;
    private String scheduledAt;
    private String method;
    private String location;
    private String status;
    private Integer score;
    private String feedback;
    /** 本轮结论：PASS/FAIL，未填写为空 */
    private String result;
    /** 面试单号 */
    private String interviewNo;
    private String feedbackAt;
}
