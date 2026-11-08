package com.laimengchao.sso.ehr.module.approval.dto;

import lombok.Data;

import java.util.List;

/**
 * 交接确认入参。
 * <p>
 * {@code itemIds} 为空表示「全部确认」；只传其中几条就只确认那几条（勾选式逐项确认）。
 * 全部确认后申请单的 {@code handoverStatus} 自动置 1，才轮到负责人审批。
 */
@Data
public class HandoverConfirmRequest {

    /** 要确认的交接项ID；为空 = 全部确认 */
    private List<String> itemIds;

    /** 确认备注 / 遗留问题，可空 */
    private String note;
}
