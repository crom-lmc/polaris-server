package com.laimengchao.sso.ehr.module.attendance.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** HR 手工补录打卡。 */
@Data
public class AttendancePunchRequest {

    private Long employeeId;
    private LocalDateTime punchTime;
    /** IN / OUT，可空（自动判定） */
    private String punchType;
    private String remark;
}
