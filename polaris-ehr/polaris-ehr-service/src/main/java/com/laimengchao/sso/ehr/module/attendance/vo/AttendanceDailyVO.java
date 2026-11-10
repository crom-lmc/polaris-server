package com.laimengchao.sso.ehr.module.attendance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 考勤日结果展示。 */
@Data
public class AttendanceDailyVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private LocalDate workDate;
    private LocalTime shiftStart;
    private LocalTime shiftEnd;
    private LocalDateTime firstIn;
    private LocalDateTime lastOut;
    private Integer lateMinutes;
    private Integer earlyMinutes;
    private Integer absentMinutes;
    private BigDecimal absentHours;
    private Integer workMinutes;
    private Integer overtimeMinutes;
    private String dayType;
    private String status;
    private String statusName;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaveId;
    private String leaveType;
    private Integer correctionStatus;
    private String remark;
}
