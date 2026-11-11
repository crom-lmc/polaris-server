package com.laimengchao.sso.job.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 调度选主租约：单行（lock_name=scheduler），谁把 expire_at 续到未来谁是 leader。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_lock")
public class JobLock extends BaseEntity {

    private String lockName;
    private String owner;
    private LocalDateTime expireAt;
}
