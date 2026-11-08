package com.laimengchao.sso.cms.module.content.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批中心回传的终审结果（CMS 侧声明一份，不复用 settings 内部类）。
 * <p>
 * {@code instanceId} / {@code bizId} 都是字符串：雪花 ID 19 位，服务端统一按字符串传，
 * 接收方自己转 Long。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalCallbackRequest {

    private String instanceId;
    private String bizType;
    private String bizId;
    private String bizNo;

    /** 0 进行中 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 已终止 */
    private Integer result;
    private String resultName;
    /** 便捷布尔：等价于 result == 1 */
    private Boolean approved;

    private Long applicantId;
    private String applicantName;
    private String templateCode;

    private Map<String, Object> formData = new LinkedHashMap<>();

    private String finishedAt;
}
