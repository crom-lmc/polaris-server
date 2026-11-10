package com.laimengchao.sso.ehr.module.recruit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 发起录用 Offer 入参；提交即进入审批中心 */
@Data
public class OfferRequest {

    @NotNull(message = "候选人必填")
    private Long candidateId;

    /** 关联招聘职位（可空，默认取候选人身上的 jobId） */
    private Long jobId;

    @NotBlank(message = "录用职位必填")
    private String position;

    @NotNull(message = "月薪必填")
    @DecimalMin(value = "0.01", message = "月薪必须大于 0")
    private BigDecimal salary;

    /** yyyy-MM-dd */
    @NotBlank(message = "预计入职日期必填")
    private String expectedOnboardDate;

    @NotNull(message = "录用部门必填")
    private Long orgId;

    private String remark;
}
