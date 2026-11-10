package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 法定年假策略：累计工龄三档天数 + 结转规则，同时只允许一条启用。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_annual_leave_policy")
public class AnnualLeavePolicy extends BaseEntity {

    private String name;
    /** 1 启用 / 0 停用 */
    private Integer enabled;
    /** 第一档起始累计工龄（年） */
    private Integer tier1Years;
    private BigDecimal tier1Days;
    private Integer tier2Years;
    private BigDecimal tier2Days;
    private Integer tier3Years;
    private BigDecimal tier3Days;
    /** 是否允许结转 */
    private Integer carryoverEnabled;
    /** 最多结转天数 */
    private BigDecimal carryoverMaxDays;
    /** 结转额度失效月份（次年） */
    private Integer carryoverExpireMonth;
    private LocalDate effectiveFrom;
    private String remark;
}
