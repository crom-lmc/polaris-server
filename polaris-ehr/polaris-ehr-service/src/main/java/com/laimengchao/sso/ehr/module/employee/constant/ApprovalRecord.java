package com.laimengchao.sso.ehr.module.employee.constant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 审批步骤记录（入职审批 / 转正审批各一步一存）。以 JSON 数组形式落在
 * ehr_employee 的 onboard_approvals / regularize_approvals 列上——审批逻辑是「写死」的
 * 两步（用人部门 → 人事主管），后续单独设计通用审批模块时再换表。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApprovalRecord {

    /** DEPT = 用人部门 / 部门主管；HR = 人事主管 */
    public String role;
    public String roleName;
    public Long accountId;
    public String name;
    /** true=通过，false=驳回 */
    public boolean pass;
    public String note;
    public String time;

    public ApprovalRecord() {
    }

    public ApprovalRecord(String role, String roleName, Long accountId, String name, boolean pass, String note, String time) {
        this.role = role;
        this.roleName = roleName;
        this.accountId = accountId;
        this.name = name;
        this.pass = pass;
        this.note = note;
        this.time = time;
    }
}
