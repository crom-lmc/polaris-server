package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 审批流水：所有动作的追加式记录，只增不改。
 * <p>
 * 转办、加签、跳过、撤回这些「非标准通过/驳回」的动作也落这里，
 * 用于事后追溯谁把单子挪走了。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appr_action")
public class ApprAction extends BaseEntity {

    private Long instanceId;
    private Long taskId;
    private Integer stepSeq;
    private Long nodeId;
    private String nodeName;

    private Long actorId;
    private String actorName;
    /** SUBMIT/APPROVE/REJECT/SKIP/TRANSFER/ADD_SIGN/CC/WITHDRAW/TERMINATE */
    private String action;
    private Long targetId;
    private String targetName;
    private String opinion;
}
