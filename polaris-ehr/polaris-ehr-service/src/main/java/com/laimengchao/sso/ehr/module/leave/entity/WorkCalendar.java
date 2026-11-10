package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** 工作日历：只落已显式配置的日期，未配置的按「周六周日休息」兜底。 */
@Data
@TableName("ehr_work_calendar")
public class WorkCalendar {

    @TableId(value = "work_date", type = IdType.INPUT)
    private LocalDate workDate;
    /** 1 工作日 / 0 休息日 */
    private Integer isWorkday;
    /** 节假日名称，非空时展示为法定节假日 */
    private String holidayName;
}
