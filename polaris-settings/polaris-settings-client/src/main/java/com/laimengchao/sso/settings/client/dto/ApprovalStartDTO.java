package com.laimengchao.sso.settings.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发起审批请求。
 * <p>
 * {@code formData} 必须放「条件表达式要用的字段」——审批模板里的条件（如请假天数 &gt; 3）
 * 就是拿它求值的。同时它会作为快照冻结，审批人看到的是提交那一刻的值。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalStartDTO {

    /** 业务场景，如 ehr.onboard */
    private String bizType;
    /** 业务主键（本次单据 ID） */
    private String bizId;
    /** 业务主体 ID；留空由审批中心按场景 subjectField 从 formData 推导 */
    private String subjectId;
    /** SUBJECT / DOC；留空由审批中心按场景配置推导 */
    private String bizIdKind;
    private String bizNo;
    /** 单据标题，如「入职审批 · 张伟」 */
    private String title;

    private Long applicantId;
    private String applicantName;
    private Long applicantOrgId;
    private String orgPath;

    private Map<String, Object> formData = new LinkedHashMap<>();

    /** 终审回调地址；空表示不回调，业务方自己查结果 */
    private String callbackUrl;
}
