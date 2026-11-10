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
    /** 结转部分已消耗 / 结转剩余（=carryoverIn-carryoverUsed，消耗优先扣结转） */
    private BigDecimal carryoverUsed;
    private BigDecimal carryoverRemaining;
    /** 是否已签署自动放弃结转（年假） */
    private Boolean carryoverWaived;
    /** 12 月待办提示标记：当年 12 月且结转剩余 >0 且未签署放弃（年假） */
    private Boolean carryoverNotice;
    /** AUTO 自动生成 / MANUAL 手工调整 */
    private String source;
    private String calcDetail;
}
