package com.laimengchao.sso.ehr.module.leave.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/** 工作日历：只查已配置日期，未配置的按「周六周日休息」兜底。 */
@Mapper
public interface WorkCalendarMapper {

    @Select("SELECT work_date FROM ehr_work_calendar WHERE is_workday = 0 "
            + "AND work_date >= #{from} AND work_date <= #{to}")
    List<LocalDate> holidays(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Select("SELECT work_date FROM ehr_work_calendar WHERE is_workday = 1 "
            + "AND work_date >= #{from} AND work_date <= #{to}")
    List<LocalDate> extraWorkdays(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
