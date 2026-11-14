package com.laimengchao.sso.ehr.module.leave.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 请假单视图（列表页字段，够用即可） */
@Data
public class LeaveVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long employeeId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
    private String applicantName;
    private String leaveType;
    private String leaveTypeName;
    private String startDate;
    private String endDate;
    private String startPeriod;
    private String endPeriod;
    /** 精确到半小时的开始/结束时间 HH:mm */
    private String startTime;
    private String endTime;
    private String dayMode;
    private BigDecimal days;
    private String reason;
    /** 销假后的实际返岗日期 */
    private String returnDate;
    /** 证明材料附件 */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long attachmentId;
    private String attachmentName;
    /** 证明材料列表（详情返回；列表一般不带以省查询） */
    private List<LeaveAttachmentVO> attachments;
    private Integer status;
    private String statusName;

    /** 提交时按组织负责人快照下来的审批人账户ID；为空表示没人配负责人，有审批权的人兜底 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approverAccountId;

    private String approverName;
    /** 审批转办人（部门负责人请假时指定）：审批通过后其审批任务由该人代收 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long delegateAccountId;
    private String delegateName;
    private String approveNote;
    private String approvedAt;
    private String createdAt;
    /** 是否是当前登录人提交的（前端据此决定要不要显示「撤销」） */
    private boolean mine;

    /* ---------- 当前在途审批（统一走审批中心后新增） ---------- */

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long approvalInstanceId;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String approvalBizType;
}
