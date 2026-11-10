package com.laimengchao.sso.ehr.module.attendance.vo;

import lombok.Data;

import java.time.LocalDate;

/** 工作日历月视图：数据库未配置的日期按法定节假日预设与周末推算，并标记来源。 */
@Data
public class WorkCalendarVO {

    private LocalDate workDate;
    /** 1 工作日 / 0 休息日 */
    private Integer isWorkday;
    private String holidayName;
    /** 数据库里是否有显式配置 */
    private Boolean isConfigured;
    /** 来源：MANUAL 手动配置 / PRESET 法定节假日预设 / DEFAULT 默认推算 */
    private String source;
}
