package com.laimengchao.sso.settings.module.approval.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发起审批的请求。业务方（ehr 等）通过 /internal/approval/start 提交。
 */
@Data
public class StartApprovalRequest {

    /** 业务场景，如 ehr.onboard */
    private String bizType;
    /** 业务主键（字符串，兼容各业务的不同主键类型） */
    private String bizId;
    /**
     * 业务主体 ID（员工 / 组织 / 文章）。留空时由场景配置的 subjectField 从 formData 推导。
     * 与 bizId 分开存，列表反查与回调定位才不用再猜 bizId 到底是谁的 ID。
     */
    private String subjectId;
    /**
     * bizId 语义：SUBJECT=业务主体 ID / DOC=本次单据 ID。
     * 留空时取场景配置 cfg_biz_scene.biz_id_kind，再兜底按 subjectId 是否等于 bizId 推断。
     */
    private String bizIdKind;
    private String bizNo;
    /** 单据标题，如「入职审批 · 张伟」 */
    private String title;

    private Long applicantId;
    private String applicantName;
    private Long applicantOrgId;
    private String orgPath;

    /** 表单快照：条件表达式从这里取值，页面 FORM 类字段也读它 */
    private Map<String, Object> formData = new LinkedHashMap<>();

    /** 终审后的回调地址，空=不回调（纯流程，业务自己查结果） */
    private String callbackUrl;
}
