package com.laimengchao.sso.ehr.module.approval.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.laimengchao.sso.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 离职 / 减员申请。
 * <p>
 * 「离职」和「减员」落到同一张表，因为审批通过后的动作完全一样（档案置离职 + 停用账号），
 * 差别只在谁发起：{@code source=SELF} 是员工自己申请，{@code source=HR} 是 HR 在人员列表上减员。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ehr_resign_apply")
public class ResignApply extends BaseEntity {

    /** 被办理人（员工档案ID） */
    private Long employeeId;
    /** 申请单号：提交时按配置中心「单号规则」生成 */
    private String applyNo;
    private String empNo;
    private String employeeName;
    private Long orgId;
    private String orgName;
    /** SELF 员工申请 / HR 减员 */
    private String source;
    /** 提交人账户ID：SELF 时就是员工本人，HR 减员时是 HR */
    private Long applicantAccountId;
    private String applicantName;
    private LocalDate resignDate;
    private String reason;
    /** 交接备注（明细在 ehr_handover_item，一条一项） */
    private String handover;
    /** 交接人员工ID + 账户ID + 姓名快照：交接人凭账户登录逐项确认 */
    private Long handoverEmployeeId;
    private Long handoverAccountId;
    private String handoverName;
    /** 0 待交接 / 1 交接已全部确认 / 2 无需交接 —— 未到 1(或2) 不准审批通过 */
    private Integer handoverStatus;
    private LocalDateTime handoverConfirmedAt;
    /**
     * 0 待部门审批 / 1 已通过 / 2 已驳回 / 3 已撤销 / 4 待人事中心审批。
     * 4 是部门负责人通过后的中间态：还要集团人力资源中心负责人终审，通过才真正置离职。
     */
    private Integer status;
    /** 提交时按「所在组织负责人」解析出来的审批人快照；为空表示没人配负责人，由有审批权的人兜底 */
    private Long approverAccountId;
    private String approverName;
    /** 审批人所在节点：被办理人本身是负责人时这里会是上一级节点，前端据此解释「为什么往上走」 */
    private String approverOrgName;
    private String approveNote;
    private LocalDateTime approvedAt;
    /**
     * 第二级审批人：集团人力资源中心负责人（提交时快照，部门通过后才开始审）。
     * 为空有两种含义——还没走到这一级，或者系统里没有「集团人力资源中心」节点（兜底：跳过二级）。
     */
    private Long hrApproverAccountId;
    private String hrApproverName;
    private String hrApproveNote;
    private LocalDateTime hrApprovedAt;
    /**
     * 提交前的员工状态（30 试用 / 40 在职）：
     * 提交时档案先置「离职中」，驳回或撤销要按这个值还原，否则人会永远卡在 50。
     * 历史单据为空，还原时按 expected_regularize_date 推断。
     */
    private Integer prevStatus;

    /**
     * 审批中心实例 ID。交接全部确认后才发起审批并写入；为空表示还在「待交接」阶段。
     * 撤销时据此撤回在途单，终审回调据此做幂等比对。
     */
    private Long approvalInstanceId;
}
