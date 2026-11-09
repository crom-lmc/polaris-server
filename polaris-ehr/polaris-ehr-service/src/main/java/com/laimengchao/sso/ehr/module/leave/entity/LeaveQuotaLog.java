package com.laimengchao.sso.ehr.module.leave.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 额度流水。biz_key 唯一，保证审批回调重复投递时只生效一次。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_leave_quota_log")
public class LeaveQuotaLog extends BaseEntity {

    private Long quotaId;
    private Long employeeId;
    private Long leaveId;
    /** 幂等键，如 LEAVE:123:DEDUCT / ADJUST:456 */
    private String bizKey;
    /** FREEZE / DEDUCT / RELEASE / ADJUST / SETTLE */
    private String action;
    private BigDecimal days;
    private BigDecimal beforeAvailable;
    private BigDecimal afterAvailable;
    private Long operatorId;
    private String remark;
}
