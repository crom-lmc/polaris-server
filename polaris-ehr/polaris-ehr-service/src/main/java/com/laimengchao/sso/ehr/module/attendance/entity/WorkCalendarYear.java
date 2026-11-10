package com.laimengchao.sso.ehr.module.attendance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作日历年度更新记录：标记某一年已通过「一键更新」写入法定节假日与调休上班日。 */
@Data
@TableName("ehr_work_calendar_year")
public class WorkCalendarYear {

    @TableId(value = "cal_year", type = IdType.INPUT)
    private Integer calYear;
    /** 一键更新执行时间 */
    private LocalDateTime appliedAt;
    /** 执行人账号名 */
    private String appliedBy;
}
