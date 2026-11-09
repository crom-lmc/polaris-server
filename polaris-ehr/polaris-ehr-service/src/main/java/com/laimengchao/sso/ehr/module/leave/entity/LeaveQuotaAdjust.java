package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 假期额度调整单：HR 发起 → 审批中心通过后才改额度，留痕可追溯。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_leave_quota_adjust")
public class LeaveQuotaAdjust extends BaseEntity {

    private Long quotaId;
    private Long employeeId;
    private String leaveType;
    private Integer year;
    /** 调整增量，可为负 */
    private BigDecimal delta;
    private String reason;
    /** 0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;
    private Long approvalInstanceId;
    private Long operatorId;
    private String operatorName;
}
