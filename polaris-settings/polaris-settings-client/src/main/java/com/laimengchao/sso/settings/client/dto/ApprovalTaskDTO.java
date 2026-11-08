package com.laimengchao.sso.settings.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/** 审批任务（客户端侧映射） */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalTaskDTO {

    private Long id;
    private Long instanceId;
    private Integer stepSeq;
    private String stepName;
    private Integer nodeSeq;
    private String nodeName;

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
