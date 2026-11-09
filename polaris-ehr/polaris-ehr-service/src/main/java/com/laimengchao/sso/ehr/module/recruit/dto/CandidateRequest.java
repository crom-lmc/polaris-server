package com.laimengchao.sso.ehr.module.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 候选人新增/编辑入参 */
@Data
public class CandidateRequest {

    @NotBlank(message = "候选人姓名必填")
    private String name;

    private String mobile;
    private String email;
    /** REFERRAL / HEADHUNTER / WEBSITE / JOB_BOARD / OTHER */
    private String source;

    @NotBlank(message = "应聘职位必填")
    private String position;

    private Long orgId;
    /** RESUME / INTERVIEW / OFFER / REJECTED，新增默认 RESUME */
    private String stage;
    private String remark;
}
