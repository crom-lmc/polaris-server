package com.laimengchao.sso.settings.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审批任务视图。前端「待我审批」列表和步骤条内的分支行都用它。
 */
@Data
public class ApprovalTaskVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long instanceId;
    private Integer stepSeq;
    private String stepName;
    private Integer nodeSeq;
    private String nodeName;
    /** v5：任务所属条件分支节点 ID；NULL=主干 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentNodeId;
    /** v5：任务路径键，前端按此分组展示分支流转 */
    private String branchPath;
    private Integer depth;
    /** v5：分支展示名（条件描述 / 兜底），由快照节点名带出 */
    private String branchName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long approverId;
    private String approverName;
    /** USER / DELEGATE */
    private String approverSource;
    private String delegateOwnerName;

    /** APPROVE / CC */
    private String taskKind;
    private Integer status;
    private String statusName;
    private String approveMode;
    private Integer seqInNode;

    private String opinion;
    private LocalDateTime actedAt;
    private LocalDateTime createdAt;
}
