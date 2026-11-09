package com.laimengchao.sso.ehr.module.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 录用 Offer：审批通过后自动生成一条待入职员工档案 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_offer")
public class Offer extends BaseEntity {

    private Long candidateId;
    private String position;
    private BigDecimal salary;
    private LocalDate expectedOnboardDate;
    private Long orgId;
    /** DRAFT / APPROVING / APPROVED / REJECTED */
    private String status;
    private Long employeeId;
    private String remark;
}
