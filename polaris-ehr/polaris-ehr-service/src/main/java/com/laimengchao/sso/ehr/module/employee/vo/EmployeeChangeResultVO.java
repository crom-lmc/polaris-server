package com.laimengchao.sso.ehr.module.employee.vo;

import lombok.Data;

/** 人员信息变更提交结果：变更单号 + 差异概览 + 是否已进入审批 */
@Data
public class EmployeeChangeResultVO {

    /** 变更单ID（ehr_emp_change.id，字符串防精度丢失） */
    private String changeId;
    /** 基本信息变更字段数 */
    private Integer baseChanges;
    /** 子表新增行数 */
    private Integer addCount;
    /** 子表修改行数 */
    private Integer modCount;
    /** 子表删除行数 */
    private Integer delCount;
    /** true=审批单已在途（实例发起即终态时为 false，说明回调已处理完） */
    private Boolean pendingApproval;
    /** 变更摘要（与审批单上的 changeSummary 一致） */
    private String changeSummary;
}
