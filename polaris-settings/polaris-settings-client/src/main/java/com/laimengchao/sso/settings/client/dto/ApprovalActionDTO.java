package com.laimengchao.sso.settings.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 审批动作：通过/驳回/转办/加签/撤回/终止 共用。
 * <p>
 * 操作人由调用方从自己的令牌里解析后填进来——settings 没有业务应用的密钥，也不该有。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalActionDTO {

    private Long operatorId;
    private String operatorName;

    /** true 通过 / false 驳回 */
    private Boolean pass;
    private String note;

    /** 转办、加签的目标账号 */
    private Long targetId;
    private String targetName;
    /** 加签位置：BEFORE / AFTER */
    private String position;
}
