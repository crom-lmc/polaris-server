package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 月度考勤汇总；HR 确认后锁定，不再随单据变化重算。 */
@Data
@TableName("ehr_attendance_month")
public class AttendanceMonth {

    @TableId(type = IdType.INPUT)
    private Long id;
    private Long employeeId;
    /** yyyy-MM */
    private String month;
    private BigDecimal shouldDays;
    private BigDecimal actualDays;
    private BigDecimal leaveDays;
    private BigDecimal overtimeHours;
    private BigDecimal absentDays;
    /** 0 未确认 / 1 已确认 */
    private Integer confirmed;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
