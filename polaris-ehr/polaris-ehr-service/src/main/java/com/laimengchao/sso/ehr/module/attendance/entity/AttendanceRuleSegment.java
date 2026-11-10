package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalTime;

/** 考勤规则时间段：一天可配置多段班次。 */
@Data
@TableName("ehr_attendance_rule_segment")
public class AttendanceRuleSegment {

    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;
    private Integer seq;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 该段计工作时长（分钟） */
    private Integer workMinutes;
    /** 是否需要打卡 */
    private Integer needPunch;
    private Integer crossDay;
}
