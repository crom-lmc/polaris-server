package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 模拟考勤生成日志：mock_date 唯一，重复触发直接跳过。 */
@Data
@TableName("ehr_attendance_mock_log")
public class AttendanceMockLog {

    @TableId(type = IdType.INPUT)
    private Long id;
    private LocalDate mockDate;
    private Integer punchCount;
    private Integer absentCount;
    private Integer overtimeCount;
    private String detail;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
