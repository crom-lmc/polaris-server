package com.laimengchao.sso.settings.module.approval.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 审批人维度时效统计。
 * <p>
 * handled = 该人处理过的 APPROVE 任务数（通过 + 驳回）；avgHours / p90Hours
 * 按任务创建到处理的时间计算，跨步骤等待也计入——反映「单子在他手里停多久」。
 */
@Data
public class ApprovalApproverStatsVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long approverId;
    private String approverName;
    private Long handled = 0L;
    private Long approved = 0L;
    private Long rejected = 0L;
    private Double avgHours = 0d;
    private Double p90Hours = 0d;
}
