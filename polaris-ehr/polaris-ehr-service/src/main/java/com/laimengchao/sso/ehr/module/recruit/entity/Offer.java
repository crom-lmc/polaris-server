package com.laimengchao.sso.ehr.module.recruit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 录用 Offer：审批通过后待 HR 线下确认，确认后办理入职建档 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_offer")
public class Offer extends BaseEntity {

    /** Offer 单号：由配置中心单号规则生成 */
    private String offerNo;
    private Long candidateId;
    private Long jobId;
    private String position;
    private BigDecimal salary;
    private LocalDate expectedOnboardDate;
    private Long orgId;
    /** DRAFT / APPROVING / APPROVED / CONFIRMED / DECLINED / REJECTED */
    private String status;
    private LocalDateTime confirmedAt;
    private Long confirmedBy;
    private Long employeeId;
    private String remark;
}
