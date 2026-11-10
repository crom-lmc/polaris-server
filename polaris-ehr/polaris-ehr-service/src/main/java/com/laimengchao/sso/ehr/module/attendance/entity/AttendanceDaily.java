package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 考勤日结果。规则改动不影响历史：rule_id + rule_snapshot 保存当日命中的规则与时间段。
 * 缺卡一律记 MISSING_PUNCH，不直接记旷工，等待补卡审批。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_attendance_daily")
public class AttendanceDaily extends BaseEntity {

    private Long employeeId;
    private LocalDate workDate;
    private Long ruleId;
    /** 规则与时间段 JSON 快照 */
    private String ruleSnapshot;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    /** 当日首次上班打卡 */
    private LocalDateTime firstIn;
    /** 当日末次下班打卡 */
    private LocalDateTime lastOut;
    private Integer lateMinutes;
    private Integer earlyMinutes;
    private Integer absentMinutes;
    /** 实际分钟/60，保留 2 位，不向上取整 */
    private BigDecimal absentHours;
    private Integer workMinutes;
    private Integer overtimeMinutes;
    /** WORKDAY / REST / LEGAL_HOLIDAY */
    private String dayType;
    /** NORMAL/LATE/EARLY/LATE_EARLY/ABSENT/MISSING_PUNCH/LEAVE/REST/HOLIDAY */
    private String status;
    private Long leaveId;
    private String leaveType;
    /** 0 无 / 1 补卡审批中 / 2 已补卡 */
    private Integer correctionStatus;
    private String remark;
}
