package com.laimengchao.sso.ehr.module.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 招聘职位新增/编辑入参 */
@Data
public class JobPostRequest {

    @NotBlank(message = "职位名称必填")
    private String title;

    private Long orgId;
    /** FULL_TIME / INTERN / PART_TIME */
    private String jobType;
    private String workCity;
    private Integer headcount;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String educationReq;
    private String experienceReq;
    private String description;
    private String requirement;
    private String tags;
    private LocalDate closeAt;
    private Integer sortNo;
    private Long ownerId;
}
