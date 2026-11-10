package com.laimengchao.sso.ehr.module.attendance.dto;

import lombok.Data;

import java.time.LocalDate;

/** 工作日历批量保存项。 */
@Data
public class WorkCalendarItem {

    private LocalDate workDate;
    /** 1 工作日 / 0 休息日 */
    private Integer isWorkday;
    /** 节假日名称，非空时按法定节假日展示 */
    private String holidayName;
}
