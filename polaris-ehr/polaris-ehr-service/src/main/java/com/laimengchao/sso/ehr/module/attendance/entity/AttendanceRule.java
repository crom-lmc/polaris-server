package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * 考勤规则：一套上下班时间 + 宽容分钟 + 是否需要打卡。
 * 具体适用人群在 {@link AttendanceRuleScope}，多段班次在 {@link AttendanceRuleSegment}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_attendance_rule")
public class AttendanceRule extends BaseEntity {

    /** 规则编码，全表唯一；DEFAULT 作为兜底规则 */
    private String code;
    private String name;
    /** 1 启用 / 0 停用 */
    private Integer status;
    private LocalTime workStart;
    private LocalTime workEnd;
    /** 午休开始/结束，计工作时长时扣除 */
    private LocalTime restStart;
    private LocalTime restEnd;
    /** 标准工作时长（小时） */
    private BigDecimal workHours;
    /** 迟到宽容分钟 */
    private Integer lateGrace;
    /** 早退宽容分钟 */
    private Integer earlyGrace;
    /** 缺勤起算最小分钟（0=按实际） */
    private Integer absentMinMinutes;
    /** 是否弹性工时 */
    private Integer flexible;
    /** 是否跨天班次 */
    private Integer crossDay;
    /** 是否需要打卡 */
    private Integer requirePunch;
    private String color;
    private String remark;
}
