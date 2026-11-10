package com.laimengchao.sso.ehr.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** 新增/修改考勤规则：基本字段与适用范围、时间段一并覆盖保存。 */
@Data
public class AttendanceRuleRequest {

    private String code;
    private String name;
    /** 1 启用 / 0 停用，默认 1 */
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
    private List<ScopeItem> scopes = new ArrayList<>();
    private List<SegmentItem> segments = new ArrayList<>();

    /** 适用范围：ALL / ORG / EMPLOYEE */
    @Data
    public static class ScopeItem {
        private String scopeType;
        private Long scopeId;
        private Integer priority;
    }

    /** 规则时间段 */
    @Data
    public static class SegmentItem {
        private Integer seq;
        private String name;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer workMinutes;
        private Integer needPunch;
        private Integer crossDay;
    }
}
