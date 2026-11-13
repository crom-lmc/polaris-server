package com.laimengchao.sso.ehr.module.attendance.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/** 提交加班：选择加班日期 + 开始/结束时间（同一天，不允许跨天），
 *  系统自动按工作日历判定类型，并扣除当天正常工作时段（含午休）后计算有效加班小时数。 */
@Data
public class OvertimeRequest {

    private LocalDate workDate;
    /** 加班开始时间（HH:mm） */
    private LocalTime startTime;
    /** 加班结束时间（HH:mm），必须严格晚于 startTime，且与 startTime 在同一天 */
    private LocalTime endTime;
    private String reason;
}
