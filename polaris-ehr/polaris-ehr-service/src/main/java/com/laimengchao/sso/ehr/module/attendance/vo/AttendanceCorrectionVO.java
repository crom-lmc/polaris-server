package com.laimengchao.sso.ehr.module.attendance.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 补卡申请展示。 */
@Data
public class AttendanceCorrectionVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    private String employeeName;
    private LocalDate workDate;
    private String type;
    private String punchType;
    private LocalDateTime punchTime;
    private String reason;
    private Integer status;
    private String statusName;
    private String approveNote;
    private LocalDateTime approvedAt;
    private String applicantName;
    private LocalDateTime createdAt;
}
