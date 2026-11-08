package com.laimengchao.sso.settings.client.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批委托登记：owner 在一段时间内把审批权交给 delegate。
 * <p>
 * 部门负责人请假时由 EHR 在请假审批通过后调用，委托窗口即请假起止时间；
 * 派单时审批中心直接把任务落到被委托人身上，任务上仍记「代 X 审批」。
 */
@Data
public class ApprovalDelegateDTO {

    /** 委托原审批人（部门负责人）账户ID */
    private Long ownerId;
    private String ownerName;
    /** 被委托人账户ID */
    private Long delegateId;
    private String delegateName;
    /** 空 = 全部业务（请假期间通常全量代收） */
    private String bizType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 幂等键：同一业务重复登记按它更新，不再插入新行 */
    private String remark;
}
