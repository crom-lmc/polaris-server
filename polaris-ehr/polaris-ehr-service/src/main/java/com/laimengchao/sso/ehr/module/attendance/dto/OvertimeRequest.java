package com.laimengchao.sso.ehr.module.attendance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 提交加班。 */
@Data
public class OvertimeRequest {

    private LocalDate workDate;
    /** 加班时长（小时） */
    private BigDecimal hours;
    /** NORMAL / HOLIDAY / RESTDAY */
    private String type;
    private String reason;
}
