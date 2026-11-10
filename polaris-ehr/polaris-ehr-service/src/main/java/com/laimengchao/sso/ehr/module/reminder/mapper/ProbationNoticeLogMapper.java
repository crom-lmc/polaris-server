package com.laimengchao.sso.ehr.module.reminder.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

/**
 * 试用期到期站内消息发送去重：同一员工同一天只发一条（唯一键幂等）。
 */
@Mapper
public interface ProbationNoticeLogMapper {

    /**
     * 幂等写入：唯一键冲突时静默跳过。返回 1 表示今天还没发过，可以发送消息；
     * 0 表示今天已发过（任务重跑/重启不会重复打扰）。
     */
    @Insert("INSERT IGNORE INTO ehr_probation_notice_log (id, employee_id, notice_date, created_at) "
            + "VALUES (#{id}, #{employeeId}, #{noticeDate}, NOW())")
    int insertIgnore(@Param("id") Long id, @Param("employeeId") Long employeeId,
                     @Param("noticeDate") LocalDate noticeDate);
}
