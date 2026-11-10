package com.laimengchao.sso.ehr.module.attendance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 员工本人提交补卡申请。 */
@Data
public class AttendanceCorrectionRequest {

    private LocalDate workDate;
    /** IN / OUT */
    private String punchType;
    private LocalDateTime punchTime;
    private String reason;
    /** MISSING_PUNCH / OVERTIME_PUNCH，默认 MISSING_PUNCH */
    private String type;
}
