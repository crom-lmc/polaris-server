package com.laimengchao.sso.ehr.module.employee.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审批中心回传的终审结果。
 * <p>
 * 字段与 settings 的 {@code ApprovalCallbackService.buildPayload()} 一一对应，
 * 但刻意在本模块声明一份而不是复用服务端类——那是 settings 的内部实现，
 * 改字段时这里跟不跟得上是本服务的判断，不该被外部 jar 牵着走。
 * <p>
 * {@code instanceId} / {@code bizId} 都是<b>字符串</b>：雪花 ID 是 19 位，
 * 在 JS 侧已经保不住精度，服务端统一按字符串传，接收方自己转 Long。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalCallbackRequest {

    private String instanceId;
    private String bizType;
    private String bizId;
    private String bizNo;
    /** 业务主体 ID（员工 / 组织），与 bizId 解耦 */
    private String subjectId;
    /** SUBJECT=业务主体 / DOC=本次单据；存量回调没有该字段时按下面方法推断 */
    private String bizIdKind;

    /** 0 进行中 / 1 已通过 / 2 已驳回 / 3 已撤回 / 4 已终止 */
    private Integer result;
    private String resultName;
    /** 便捷布尔：等价于 result == 1，业务方按它分支就够 */
    private Boolean approved;

    private Long applicantId;
    private String applicantName;
    private String templateCode;

    /** 提交时冻结的表单快照；回调里带回来是为业务方省一次查询 */
    private Map<String, Object> formData = new LinkedHashMap<>();

    private String finishedAt;

    /**
     * bizId 是否指向「本次单据」。补列前的存量回调没有 bizIdKind，
     * 用「主体 ID 与 bizId 是否相同」兜底：相同即老口径 SUBJECT。
     */
    public boolean docBizId() {
        if ("DOC".equalsIgnoreCase(bizIdKind)) {
            return true;
        }
        if ("SUBJECT".equalsIgnoreCase(bizIdKind)) {
            return false;
        }
        return subjectId != null && !subjectId.isBlank() && !subjectId.equals(bizId);
    }
}
