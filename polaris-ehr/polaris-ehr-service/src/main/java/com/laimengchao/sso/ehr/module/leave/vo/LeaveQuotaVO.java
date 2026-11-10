package com.laimengchao.sso.ehr.module.leave.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/** 员工假期额度视图：available = total - used - frozen */
@Data
public class LeaveQuotaVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String leaveType;
    private String leaveTypeName;
    private Integer year;
    /** 周期起止日（自然年） */
    private String periodStart;
    private String periodEnd;
    private BigDecimal totalDays;
    private BigDecimal usedDays;
    private BigDecimal frozenDays;
    private BigDecimal availableDays;
    /** 上期结转进来 / 结转到下期 / 结转失效日 */
    private BigDecimal carryoverIn;
    private BigDecimal carryoverOut;
    private String carryoverExpireAt;
    /** AUTO 自动生成 / MANUAL 手工调整 */
    private String source;
    private String calcDetail;
}
