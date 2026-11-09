package com.laimengchao.sso.settings.module.approval.vo;

import lombok.Data;

/**
 * 审批时效统计（场景 / 组织维度）。
 * <p>
 * 口径：total 为时间范围内发起的全部单据；backlog 为其中仍在途的数量；
 * avgHours / p90Hours 只统计已结束（通过 / 驳回 / 终止）的单据，单位小时；
 * rejectRate = 驳回数 /(通过数 + 驳回数) × 100，没有样本时为 0。
 */
@Data
public class ApprovalStatsVO {

    private Long total = 0L;
    private Long approved = 0L;
    private Long rejected = 0L;
    private Long withdrawn = 0L;
    private Long terminated = 0L;
    private Long backlog = 0L;
    /** 平均处理时长（小时） */
    private Double avgHours = 0d;
    /** P90 处理时长（小时） */
    private Double p90Hours = 0d;
    /** 驳回率（百分比 0-100） */
    private Double rejectRate = 0d;
}
