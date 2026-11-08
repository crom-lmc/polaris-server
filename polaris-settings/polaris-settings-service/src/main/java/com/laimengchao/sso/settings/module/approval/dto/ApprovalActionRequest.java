package com.laimengchao.sso.settings.module.approval.dto;

import lombok.Data;

/**
 * 审批动作请求：通过/驳回/转办/加签/抄送 共用。
 */
@Data
public class ApprovalActionRequest {

    /** 当前操作人（由调用方从令牌解析后传入，settings 自己不看令牌） */
    private Long operatorId;
    private String operatorName;

    /** 通过 true / 驳回 false */
    private Boolean pass;
    /** 审批意见 */
    private String note;

    /** 转办、加签的目标账号 */
    private Long targetId;
    private String targetName;
    /** 加签位置：BEFORE 前加签 / AFTER 后加签 */
    private String position;
}
