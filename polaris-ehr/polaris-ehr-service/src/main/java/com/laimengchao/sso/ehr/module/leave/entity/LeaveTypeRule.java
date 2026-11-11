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
    /** 允许的证明文件类型，如 pdf,jpg；空=不限 */
    private String proofTypes;
    /** 请假折算小时达到该值才强制证明（0=所有时长都要） */
    private BigDecimal proofMinHours;
    /** 证明上传提示（员工手册口径） */
    private String proofHint;
    private BigDecimal minDays;
    private BigDecimal maxDaysPerRequest;
    private Integer minNoticeDays;
    private Integer allowPastDays;
    private Integer needDelegate;
    /** 是否只能一次性休完（婚假、产假/陪产假等，天数口径见员工手册） */
    private Integer oneTimeOnly;
    private Integer sortNo;
    private LocalDateTime updatedAt;
}
