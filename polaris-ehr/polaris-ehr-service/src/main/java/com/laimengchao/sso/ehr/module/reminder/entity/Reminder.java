package com.laimengchao.sso.ehr.module.reminder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 到期提醒：唯一键 (employee_id,type,trigger_date,receiver_id) 保证同一节点只提醒一次 */
@Data
@TableName("ehr_reminder")
public class Reminder {

    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    /** 接收人统一账号 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long receiverId;
    /** PROBATION_DUE / CONTRACT_DUE */
    private String type;
    /** 业务主键：员工 ID 或合同 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;
    private LocalDate triggerDate;
    private String title;
    private String content;
    /** 0 未读 / 1 已读 */
    private Integer readFlag;
    private LocalDateTime createdAt;

    /** 展示用：员工姓名，不落库 */
    @TableField(exist = false)
    private String employeeName;
}
