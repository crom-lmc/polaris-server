package com.laimengchao.sso.settings.module.approval.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批委托登记请求：部门负责人请假通过后，EHR 按请假起止时间登记委托。
 * <p>
 * remark 作幂等键：同一请假单重复回调只更新，不重复插入。
 */
@Data
public class ApprovalDelegateRequest {

    /** 委托原审批人（部门负责人）账户ID */
    private Long ownerId;
    private String ownerName;
    /** 被委托人账户ID */
    private Long delegateId;
    private String delegateName;
    /** 空 = 全部业务 */
    private String bizType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String remark;
}
