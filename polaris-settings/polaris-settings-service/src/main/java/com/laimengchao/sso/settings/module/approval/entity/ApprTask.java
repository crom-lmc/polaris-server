package com.laimengchao.sso.settings.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审批任务：一个审批人一条。
 * <p>
 * 「待我审批」= (approver_id, status=0, task_kind='APPROVE')；代收就是这里 approver_source=DELEGATE
 * 且 delegate_owner_id 有值，前端显示「代 X 审批」。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appr_task")
public class ApprTask extends BaseEntity {

    private Long instanceId;
    private Integer stepSeq;
    private String stepName;
    /** 分支节点 ID（快照里的，可能对应不上当前模板） */
    private Long nodeId;
    private Integer nodeSeq;
    private String nodeName;
    /** v5：该任务所属条件分支节点 ID（网关分支卡=自身 nodeId；子链任务=所属分支 nodeId） */
    private Long parentNodeId;
    /** v5：任务路径键，供分组展示与汇合判断 */
    private String branchPath;
    /** v5：嵌套深度，详情页据此做树形分组 */
    private Integer depth;

    private Long approverId;
    private String approverName;
    /** USER 直接派单 / DELEGATE 委托代收 */
    private String approverSource;
    private Long delegateOwnerId;
    private String delegateOwnerName;

    /** APPROVE 审批 / CC 抄送（不阻塞流程） */
    private String taskKind;
    /** 0 待办 1 通过 2 驳回 3 跳过 4 作废 9 已转办 */
    private Integer status;
    private String approveMode;
    /** 会签/依次模式下节点内序号 */
    private Integer seqInNode;

    private String opinion;
    private LocalDateTime actedAt;
    private LocalDateTime dueAt;
}
