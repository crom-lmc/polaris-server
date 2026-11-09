package com.laimengchao.sso.ehr.module.reminder.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laimengchao.sso.ehr.module.reminder.entity.JobLock;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface JobLockMapper extends BaseMapper<JobLock> {

    /**
     * 原子抢锁：行不存在则插入；已过期则覆盖；未过期则原样保留。
     * 返回 &gt;0 表示抢到锁（插入 1 行或更新成功），0 表示别人正在跑。
     */
    @Insert("INSERT INTO ehr_job_lock (job_name, locked_until, locked_by) "
            + "VALUES (#{jobName}, #{lockedUntil}, #{lockedBy}) "
            + "ON DUPLICATE KEY UPDATE "
            + "locked_until = IF(locked_until < #{now}, #{lockedUntil}, locked_until), "
            + "locked_by = IF(locked_until < #{now}, #{lockedBy}, locked_by)")
    int tryLock(@Param("jobName") String jobName, @Param("lockedUntil") LocalDateTime lockedUntil,
                @Param("lockedBy") String lockedBy, @Param("now") LocalDateTime now);
}
