package com.laimengchao.sso.ehr.module.reminder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.reminder.entity.Reminder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReminderMapper extends BaseMapper<Reminder> {

    /**
     * 幂等写入：唯一键冲突时静默跳过，任务重复执行不会产生重复提醒。
     * 返回 1 表示新写入一条，0 表示该节点已提醒过。
     */
    @Insert("INSERT IGNORE INTO ehr_reminder "
            + "(id, employee_id, receiver_id, type, biz_id, trigger_date, title, content, read_flag, created_at) "
            + "VALUES (#{id}, #{employeeId}, #{receiverId}, #{type}, #{bizId}, #{triggerDate}, "
            + "#{title}, #{content}, 0, NOW())")
    int insertIgnore(@Param("id") Long id, @Param("employeeId") Long employeeId,
                     @Param("receiverId") Long receiverId, @Param("type") String type,
                     @Param("bizId") Long bizId, @Param("triggerDate") java.time.LocalDate triggerDate,
                     @Param("title") String title, @Param("content") String content);
}
