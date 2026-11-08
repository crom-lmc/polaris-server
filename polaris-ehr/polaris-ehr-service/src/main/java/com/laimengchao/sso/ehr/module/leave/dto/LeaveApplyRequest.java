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

    @NotBlank(message = "请假事由必填")
    private String reason;

    /** 审批转办人账户ID：部门负责人请假必填，其他人可选 */
    private Long delegateAccountId;
}
