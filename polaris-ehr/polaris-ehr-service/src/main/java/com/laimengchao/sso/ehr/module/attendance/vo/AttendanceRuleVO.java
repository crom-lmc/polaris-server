package com.laimengchao.sso.ehr.module.attendance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRuleScope;
import com.laimengchao.sso.ehr.module.attendance.entity.AttendanceRuleSegment;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** 考勤规则详情：基本信息 + 适用范围 + 时间段。 */
@Data
public class AttendanceRuleVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String code;
    private String name;
    private Integer status;
    private LocalTime workStart;
    private LocalTime workEnd;
    private LocalTime restStart;
    private LocalTime restEnd;
    private BigDecimal workHours;
    private Integer lateGrace;
    private Integer earlyGrace;
    private Integer absentMinMinutes;
    private Integer flexible;
    private Integer crossDay;
    private Integer requirePunch;
    private String color;
    private String remark;
    private List<AttendanceRuleScope> scopes;
    private List<AttendanceRuleSegment> segments;
}
