package com.laimengchao.sso.ehr.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 转正申请（试用期员工 → 正式员工）。
 * <p>
 * 之前「提交转正申请」是直接对 {@code ehr_employee} 打标记并发起审批，
 * 审批人只能看到档案上的既有字段，不知道申请人自己说了什么；
 * 而且被驳回后重提没有留痕。这张表把「申请」作为独立业务实体：
 * 申请人填的内容、每次提交的状态都留档，审批人看的是这张表。
 * <p>
 * 状态：0 审批中 → 1 已通过（档案置正式在职）/ 2 已驳回 / 3 已撤销。
 * 审批通过后的动作（档案置 40、regularizeStatus=3）由
 * {@code EmployeeService#handleRegularizeApprovalResult} 执行，本表只记录申请与审批指针。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_regularize_apply")
public class RegularizeApply extends BaseEntity {

    /** 员工档案ID */
    private Long employeeId;
    /** 申请单号：提交时按配置中心「单号规则」生成 */
    private String applyNo;
    private String empNo;
    private String employeeName;
    private Long orgId;
    private String orgName;
    /** 组织全路径快照，审批页展示用 */
    private String orgPath;
    private String position;
    /** 入职日期快照 */
    private LocalDate hireDate;
    /** 试用期月数快照（免试用期时为 0） */
    private Integer probationMonths;
    /** 预计转正日期快照（按试用期推算） */
    private LocalDate expectedRegularizeDate;
    /** 申请人填写的转正日期 */
    private LocalDate regularizeDate;
    /** 试用期工作自评 */
    private String selfReview;
    /** 试用期工作业绩与成果 */
    private String workSummary;
    /** 转正申请理由 */
    private String reason;
    /** 转正后的工作目标与计划 */
    private String nextPlan;
    /** 0 审批中 / 1 已通过 / 2 已驳回 / 3 已撤销 */
    private Integer status;
    private Long applicantAccountId;
    private String applicantName;
    /** 审批中心实例ID；为空表示尚未成功发起 */
    private Long approvalInstanceId;
}
