package com.laimengchao.sso.ehr.module.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 候选人新增/编辑入参 */
@Data
public class CandidateRequest {

    @NotBlank(message = "候选人姓名必填")
    private String name;

    private String mobile;
    private String email;
    /** REFERRAL / HEADHUNTER / WEBSITE / JOB_BOARD / PORTAL / OTHER */
    private String source;

    @NotBlank(message = "应聘职位必填")
    private String position;

    private Long orgId;
    /** RESUME / INTERVIEW / OFFER / REJECTED，新增默认 RESUME */
    private String stage;
    private String remark;

    private Long jobId;
    /** 0未知/1男/2女 */
    private Integer gender;
    private LocalDate birthday;
    private String education;
    private String school;
    private String major;
    private BigDecimal workYears;
    private String currentCompany;
    private BigDecimal expectedSalary;
    /** 证件号：仅 HR 可录入 */
    private String idCard;
    /** 主简历附件ID（biz_type=CANDIDATE） */
    private Long resumeFileId;
}
