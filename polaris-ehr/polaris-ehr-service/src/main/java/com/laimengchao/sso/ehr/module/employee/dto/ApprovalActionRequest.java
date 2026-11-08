package com.laimengchao.sso.ehr.module.employee.dto;

import lombok.Data;

/** 审批动作入参：pass=通过/驳回，note=审批意见 */
@Data
public class ApprovalActionRequest {
    /** 默认 true（通过）；传 false 表示驳回 */
    private Boolean pass;
    private String note;
}
