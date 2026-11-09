package com.laimengchao.sso.ehr.module.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感操作审计。只做 INSERT 与查询，没有逻辑删除/更新时间，
 * 因此不继承 BaseEntity（否则会带上库里不存在的列）。
 */
@Data
@TableName("ehr_audit_log")
public class AuditLog {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorId;
    private String operatorName;
    /** VIEW / PLAIN / EXPORT */
    private String action;
    /** EMPLOYEE / LEAVE / CONTRACT... */
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    /** 涉及的敏感字段，逗号分隔 */
    private String fields;
    private String ip;
    private String ua;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 展示用：目标对象名称（如员工姓名），不落库 */
    @TableField(exist = false)
    private String targetName;
}
