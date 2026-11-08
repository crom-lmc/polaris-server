package com.laimengchao.sso.ehr.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 复职申请（离职 / 减员后再入职）。
 * <p>
 * 为什么单独一张表而不是复用 {@code ehr_resign_apply}：复职是「从已离职回到在职」，
 * 与离职单的字段、状态机、审批链路都不同；混在一张表里会让两边都塞满用不上的列。
 * <p>
 * 状态：0 审批中 → 1 已通过（档案置回在职）/ 2 已驳回 / 3 已撤销。
 * 审批通过后的动作交给 {@code EmployeeService#reinstate}，本表只记录申请与审批指针。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_rehire_apply")
public class RehireApply extends BaseEntity {

    /** 员工档案ID（当前 status=60） */
    private Long employeeId;
    private String empNo;
    private String employeeName;
    private Long orgId;
    private String orgName;
    /** 原离职日期快照，审批人据此判断离职时长 */
    private LocalDate resignDate;
    /** 拟复职日期 */
    private LocalDate rehireDate;
    private String reason;
    /** 复职时是否同时启用统一账号 */
    private Integer enableAccount;
    private Integer status;
    private Long applicantAccountId;
    private String applicantName;
    /** 审批中心实例ID；为空表示尚未成功发起 */
    private Long approvalInstanceId;
}
