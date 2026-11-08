package com.laimengchao.sso.settings.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批单（客户端侧映射）。业务方用它判断「这单审批到哪了」「能不能再发起」。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalInstanceDTO {

    private Long id;
    private String bizType;
    private String bizName;
    private String bizId;
    private String bizNo;
    private String title;

    private Long applicantId;
    private String applicantName;
    private String orgPath;

    private String templateCode;
    private Integer templateVersion;

    /** 0 进行中 1 已通过 2 已驳回 3 已撤回 4 已终止 */
    private Integer status;
    private String statusName;
    private Integer currentStep;
    private Integer totalStep;
    private Integer callbackStatus;

    private LocalDateTime createdAt;
    private LocalDateTime finishedAt;

    private Map<String, Object> formData = new LinkedHashMap<>();
    private List<ApprovalTaskDTO> tasks = new ArrayList<>();
    private List<StepDTO> steps = new ArrayList<>();

    /** 当前登录人的待办任务，无则 null */
    private Long myTaskId;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StepDTO {
        private Integer seq;
        private String name;
        /** DONE / CURRENT / TODO / SKIPPED */
        private String state;
    }
}
