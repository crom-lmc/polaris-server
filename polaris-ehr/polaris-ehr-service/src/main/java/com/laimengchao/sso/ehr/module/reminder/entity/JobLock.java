package com.laimengchao.sso.ehr.module.reminder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 定时任务锁：多实例部署时靠它保证每天只跑一次 */
@Data
@TableName("ehr_job_lock")
public class JobLock {

    @TableId(type = IdType.INPUT)
    private String jobName;
    private LocalDateTime lockedUntil;
    private String lockedBy;
}
