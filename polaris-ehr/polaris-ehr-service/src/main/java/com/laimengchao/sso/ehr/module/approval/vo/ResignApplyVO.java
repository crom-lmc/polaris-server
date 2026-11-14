package com.laimengchao.sso.ehr.module.approval.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/** 离职 / 减员申请视图 */
@Data
public class ResignApplyVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;
    /** 申请单号 */
    private String applyNo;
    private String empNo;
    private String employeeName;
    private String orgName;
    /** SELF / HR */
    private String source;
    private String sourceName;
    private String applicantName;
    private String resignDate;
    private String reason;
    private String handover;

    /* ---------- 工作交接 ---------- */

    @JsonSerialize(using = ToStringSerializer.class)
    private Long handoverEmployeeId;

    /** 交接人账户ID：前端据此判断「这条是不是轮到我去确认交接」 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long handoverAccountId;
    private String handoverName;
    /** 0 待交接 / 1 交接已全部确认 / 2 无需交接 */
    private Integer handoverStatus;
    private String handoverStatusName;
    /** 形如「2/5 已确认」，列表里一行就能看出进度 */
    private String handoverProgress;
    /** 已确认项数 / 总项数 */
    private int handoverDone;
    private int handoverTotal;
    /** 交接全部完成（含「无需交接」），此时才允许审批通过 */
    private boolean handoverCompleted;
    private String handoverConfirmedAt;
    /** 交接项目清单（列表页也带上，弹窗直接用，省一次请求） */
    private java.util.List<HandoverItemVO> handoverItems;

    /** 当前登录人是否就是这条的交接人 */
    private boolean handoverMine;

    private Integer status;
    private String statusName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantAccountId;

    /** 一级（部门负责人）审批人账户ID：前端据此判断「轮到我审了吗」 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approverAccountId;
    private String approverName;
    /** 审批人所在节点名：被办理人自己是负责人时，这里是上一级节点 */
    private String approverOrgName;
    private String approveNote;
    private String approvedAt;

    /* ---------- 二级：集团人力资源中心负责人 ---------- */

    /** 二级审批人账户ID；null=未走到该环节，或系统无「集团人力资源中心」节点（有权限者兜底） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long hrApproverAccountId;
    private String hrApproverName;
    private String hrApproveNote;
    private String hrApprovedAt;

    private String createdAt;

    /** 当前登录人是否就是提交人 */
    private boolean mine;

    /* ---------- 当前在途审批（统一走审批中心后新增） ---------- */

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String approvalBizType;
}
