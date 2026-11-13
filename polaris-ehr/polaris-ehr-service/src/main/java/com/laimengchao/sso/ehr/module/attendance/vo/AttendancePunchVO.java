package com.laimengchao.sso.ehr.module.attendance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/** 打卡流水展示：回填员工姓名与工号，避免列表只显示员工 ID。 */
@Data
public class AttendancePunchVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private String employeeNo;
    private LocalDateTime punchTime;
    /** IN / OUT，空=自动判定 */
    private String punchType;
    /** DEVICE / MOCK / MANUAL / CORRECTION */
    private String source;
    private String deviceNo;
    private String location;
    private String remark;
    private LocalDateTime createdAt;
}
