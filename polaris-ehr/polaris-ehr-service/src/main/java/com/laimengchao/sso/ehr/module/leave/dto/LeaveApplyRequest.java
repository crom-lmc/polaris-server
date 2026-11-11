package com.laimengchao.sso.ehr.module.leave.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 请假申请入参；日期为 yyyy-MM-dd，天数由服务端按自然日计算 */
@Data
public class LeaveApplyRequest {

    @NotBlank(message = "请假类型必填")
    private String leaveType;

    @NotBlank(message = "开始日期必填")
    private String startDate;

    @NotBlank(message = "结束日期必填")
    private String endDate;

    /** 开始半天：AM / PM，默认 AM（旧字段：未传时间时兜底） */
    private String startPeriod;

    /** 结束半天：AM / PM，默认 PM（旧字段：未传时间时兜底） */
    private String endPeriod;

    /** 开始时间 HH:mm，按半小时选择（如 09:00 / 09:30） */
    private String startTime;

    /** 结束时间 HH:mm，按半小时选择，必须晚于开始时间且至少 30 分钟 */
    private String endTime;

    /** NATURAL 自然日 / WORKDAY 工作日（默认） */
    private String dayMode;

    /** 证明材料附件ID：病假/婚假/产假等规则要求必传（旧字段，保留兼容） */
    private Long attachmentId;

    /** 证明材料附件ID列表：新前端一单可传多个证明 */
    private List<Long> attachmentIds;

    @NotBlank(message = "请假事由必填")
    private String reason;

    /** 审批转办人账户ID：部门负责人请假必填，其他人可选 */
    private Long delegateAccountId;
}
