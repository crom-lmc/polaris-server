package com.laimengchao.sso.ehr.module.leave.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/** 年假台账行：每人当年产生 / 上年结转 / 已用 / 剩余 */
@Data
public class AnnualLeaveLedgerVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String empNo;
    private String name;
    private String orgName;
    private String hireDate;
    private String resignDate;
    private Integer status;
    private String statusName;

    private Integer year;
    /** 年假档位：5 / 10 / 15（年内跨档显示 5→10） */
    private String tier;
    /** 当年按规则产生的年假天数 */
    private BigDecimal generatedDays;
    /** 上年结转进来的天数 */
    private BigDecimal carryoverIn;
    /** 结转剩余（未休完，用于 12 月到期提示） */
    private BigDecimal carryoverRemaining;
    private BigDecimal usedDays;
    private BigDecimal frozenDays;
    /** 剩余可用 = 总额度 - 已用 - 冻结 */
    private BigDecimal availableDays;
    private BigDecimal totalDays;
    private String carryoverExpireAt;
    /** 未生成当年额度 */
    private Boolean missing;
    private String calcDetail;
}
