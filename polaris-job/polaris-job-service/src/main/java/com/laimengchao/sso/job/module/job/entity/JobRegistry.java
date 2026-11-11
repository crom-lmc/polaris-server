package com.laimengchao.sso.job.module.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 执行器注册表：应用实例启动时上报 Handler 清单，之后靠心跳维持在线。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_registry")
public class JobRegistry extends BaseEntity {

    private String appCode;
    /** 实例地址 host:port */
    private String address;
    /** 上报的 Handler 清单 JSON：[{"name":"annualLeaveRefresh","description":""}] */
    private String handlers;
    private String version;
    private Integer jobCount;
    /** 最近一次上报状态；在线判定以 lastHeartbeat 为准 */
    private Integer online;
    private LocalDateTime lastHeartbeat;
}
