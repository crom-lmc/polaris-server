package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 补卡申请：缺卡默认待补，不直接记旷工；HR 通过后回写打卡流水并重算当日。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_attendance_correction")
public class AttendanceCorrection extends BaseEntity {

    /** 补卡单号：由配置中心单号规则生成 */
    private String correctionNo;
    private Long employeeId;
    private LocalDate workDate;
    /** MISSING_PUNCH 补卡 / OVERTIME_PUNCH 加班补卡 */
    private String type;
    /** IN / OUT */
    private String punchType;
    private LocalDateTime punchTime;
    private Long overtimeId;
    private BigDecimal hours;
    private String reason;
    /** 0 待审批 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;
    private Long approvalInstanceId;
    private Long applicantAccountId;
    private String applicantName;
    private String approveNote;
    private LocalDateTime approvedAt;
}
