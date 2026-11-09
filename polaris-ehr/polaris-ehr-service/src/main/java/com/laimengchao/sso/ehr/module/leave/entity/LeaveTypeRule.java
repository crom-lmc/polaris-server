package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 请假类型规则：是否占额度、是否要证明、天数区间、提前申请、是否必须转办。 */
@Data
@TableName("ehr_leave_type_rule")
public class LeaveTypeRule {

    @TableId
    private String leaveType;
    private Integer enabled;
    private Integer quotaRequired;
    /** 年度默认额度：首次为该员工建额度时使用 */
    private BigDecimal defaultQuotaDays;
    private Integer attachmentRequired;
    private BigDecimal minDays;
    private BigDecimal maxDaysPerRequest;
    private Integer minNoticeDays;
    private Integer allowPastDays;
    private Integer needDelegate;
    private Integer sortNo;
    private LocalDateTime updatedAt;
}
