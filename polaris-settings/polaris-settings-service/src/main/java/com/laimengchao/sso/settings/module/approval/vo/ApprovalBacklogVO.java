package com.laimengchao.sso.settings.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积压单（在途且当前步骤已有待办）。
 * <p>
 * waitingHours 从当前节点最早一条待办的创建时间算起；slaHours 取自实例快照里
 * 当前步骤的时效配置，为空表示该步骤未设时效；overdue = 配置了时效且已超时。
 */
@Data
public class ApprovalBacklogVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String apprNo;
    private String title;
    private String bizName;
    private String bizType;
    private String applicantName;
    private String orgPath;
    private Integer currentStep;
    private Integer totalStep;
    private String stepName;
    private LocalDateTime submittedAt;
    /** 当前节点最早一条待办的创建时间 */
    private LocalDateTime waitingSince;
    private Double waitingHours;
    private Integer slaHours;
    private Boolean overdue;
    private String approverNames;
    private Integer urgeCount;
    private LocalDateTime lastUrgeAt;
}
