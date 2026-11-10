package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 考勤规则适用范围：员工 > 部门（最近祖先） > 全员。 */
@Data
@TableName("ehr_attendance_rule_scope")
public class AttendanceRuleScope {

    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ruleId;
    /** ALL / ORG / EMPLOYEE */
    private String scopeType;
    /** 组织ID或员工ID，ALL 为 0 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scopeId;
    /** 同层级内越大越优先 */
    private Integer priority;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
