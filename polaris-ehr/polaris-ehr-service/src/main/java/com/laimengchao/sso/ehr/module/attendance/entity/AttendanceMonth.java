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
    /** 迟到分钟数合计 */
    private Integer lateMinutes;
    /** 早退分钟数合计 */
    private Integer earlyMinutes;
    /** 缺勤小时数（按实际分钟/60，不向上取整） */
    private BigDecimal absentHours;
    /** 实际出勤小时数 */
    private BigDecimal workHours;
    /** 0 未确认 / 1 已确认 */
    private Integer confirmed;
    /** 最近一次解锁原因（解锁需 attendance:month:unlock） */
    private String unlockReason;
    private LocalDateTime unlockedAt;
    private Long unlockedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
