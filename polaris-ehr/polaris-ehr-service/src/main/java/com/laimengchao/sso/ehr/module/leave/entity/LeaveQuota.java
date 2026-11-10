package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 员工假期额度：按 员工 + 假期类型 + 周期起始日 唯一。可用 = total - used - frozen。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_leave_quota")
public class LeaveQuota extends BaseEntity {

    private Long employeeId;
    private String leaveType;
    /** MySQL 里 YEAR 是关键字，显式加反引号 */
    @TableField("`year`")
    private Integer year;
    /** 额度周期起始日（自然年为当年 1 月 1 日），唯一键的一部分 */
    private LocalDate periodStart;
    /** 额度周期截止日（自然年为当年 12 月 31 日） */
    private LocalDate periodEnd;
    /** 周期序号，续期递增 */
    private Integer periodNo;
    private BigDecimal totalDays;
    private BigDecimal usedDays;
    private BigDecimal frozenDays;
    /** 上期结转进来 */
    private BigDecimal carryoverIn;
    /** 结转到下期 */
    private BigDecimal carryoverOut;
    /** 结转部分已消耗（冻结+已用中归属上年结转的量，消耗优先扣结转） */
    private BigDecimal carryoverUsed;
    /** 结转额度失效日 */
    private LocalDate carryoverExpireAt;
    /** 来源 AUTO 自动生成 / MANUAL 手工 / CARRYOVER 结转 */
    private String source;
    /** 生成依据快照（工龄/档位/折算） */
    private String calcDetail;
    private Integer version;
}
