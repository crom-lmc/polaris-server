package com.laimengchao.sso.ehr.module.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 一轮面试中的一位面试官记录。
 * <p>同一轮可以约多位面试官（每人一行），HR 在该轮全部反馈后做通过/不通过判定，
 * 判定结果同步写到同轮所有行（round_result）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_interview")
public class Interview extends BaseEntity {

    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_CANCELED = "CANCELED";

    public static final String RESULT_PASS = "PASS";
    public static final String RESULT_FAIL = "FAIL";

    private Long candidateId;
    private Long jobId;
    /** 面试单号：IV-yyyyMMdd-6位流水 */
    private String interviewNo;
    /** 第几轮，从 1 开始 */
    private Integer roundNo;
    private Long interviewerId;
    /** 面试官统一账号ID；为空表示未开户，不发待办 */
    private Long interviewerAccountId;
    private String interviewerName;
    private LocalDateTime scheduledAt;
    /** ONSITE / VIDEO / PHONE */
    private String method;
    private String location;
    /** SCHEDULED / DONE / CANCELED */
    private String status;
    private Integer score;
    private String feedback;
    private LocalDateTime feedbackAt;
    /** HR 轮次判定：PASS / FAIL / null */
    private String roundResult;
    private LocalDateTime decidedAt;
    private Long decidedBy;
    private String remark;
}
