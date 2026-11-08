package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批委托：owner 在一段时间内把某类业务的审批权交给 delegate。
 * <p>
 * 与「转办」的区别：转办是把某一条任务永久给出去；委托是时间段内全量代收，
 * 派单时就落到被委托人身上，并记录原审批人，前端显示「代 X 审批」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appr_delegate")
public class ApprDelegate extends BaseEntity {

    private Long ownerId;
    private String ownerName;
    private Long delegateId;
    private String delegateName;
    /** 空 = 全部业务 */
    private String bizType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer enabled;
    private String remark;
}
