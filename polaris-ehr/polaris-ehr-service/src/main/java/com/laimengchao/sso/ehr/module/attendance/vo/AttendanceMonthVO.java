package com.laimengchao.sso.ehr.module.attendance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/** 月度考勤汇总展示。 */
@Data
public class AttendanceMonthVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private String month;
    private BigDecimal shouldDays;
    private BigDecimal actualDays;
    private BigDecimal leaveDays;
    private BigDecimal overtimeHours;
    private BigDecimal absentDays;
    private Integer lateMinutes;
    private Integer earlyMinutes;
    private BigDecimal absentHours;
    private BigDecimal workHours;
    /** 0 未确认 / 1 已确认 */
    private Integer confirmed;
}
