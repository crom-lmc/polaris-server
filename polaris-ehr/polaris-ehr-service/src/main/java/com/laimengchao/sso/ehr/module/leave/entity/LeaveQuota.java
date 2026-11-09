package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 员工假期额度：按 员工 + 假期类型 + 年度 唯一。可用 = total - used - frozen。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_leave_quota")
public class LeaveQuota extends BaseEntity {

    private Long employeeId;
    private String leaveType;
    /** MySQL 里 YEAR 是关键字，显式加反引号 */
    @TableField("`year`")
    private Integer year;
    private BigDecimal totalDays;
    private BigDecimal usedDays;
    private BigDecimal frozenDays;
    private Integer version;
}
