package com.laimengchao.sso.ehr.module.leave.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 请假申请入参；日期为 yyyy-MM-dd，天数由服务端按自然日计算 */
@Data
public class LeaveApplyRequest {

    @NotBlank(message = "请假类型必填")
    private String leaveType;

    @NotBlank(message = "开始日期必填")
    private String startDate;

    @NotBlank(message = "结束日期必填")
    private String endDate;

    /** 开始半天：AM / PM，默认 AM */
    private String startPeriod;

    /** 结束半天：AM / PM，默认 PM */
    private String endPeriod;

    /** NATURAL 自然日（默认） / WORKDAY 工作日 */
    private String dayMode;

    /** 证明材料附件ID：病假/婚假/产假等规则要求必传 */
    private Long attachmentId;

    @NotBlank(message = "请假事由必填")
    private String reason;

    /** 审批转办人账户ID：部门负责人请假必填，其他人可选 */
    private Long delegateAccountId;
}
