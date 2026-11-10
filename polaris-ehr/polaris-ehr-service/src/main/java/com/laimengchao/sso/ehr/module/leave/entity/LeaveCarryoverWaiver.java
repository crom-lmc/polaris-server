package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 结转年假自动放弃签署记录：员工 + 结转所在年度 唯一，签署后不再发送待办。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_leave_carryover_waiver")
public class LeaveCarryoverWaiver extends BaseEntity {

    private Long employeeId;
    /** 结转额度所在年度（在该年 12 月签署放弃） */
    private Integer year;
    /** 当年年假额度ID */
    private Long quotaId;
    /** 放弃的结转剩余天数 */
    private BigDecimal waiveDays;
    private String remark;
}
