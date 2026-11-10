package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 打卡流水：设备/模拟/手工/补卡共用一张表。 */
@Data
@TableName("ehr_attendance_punch")
public class AttendancePunch {

    @TableId(type = IdType.INPUT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private LocalDateTime punchTime;
    /** IN / OUT，空=自动判定 */
    private String punchType;
    /** DEVICE / MOCK / MANUAL / CORRECTION */
    private String source;
    private String deviceNo;
    private String location;
    private String remark;
    @TableLogic
    private Integer deleted = 0;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
