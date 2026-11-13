package com.laimengchao.sso.ehr.module.leave.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 请假时长试算：与提交请假共用同一套天数口径（工作日历 / 半天 / 城市政策固定天数），
 * 供申请弹窗「本次请假时长」实时展示，避免前后端各算一套。
 */
@Data
public class LeavePreviewVO {

    /** 本次请假时长（天） */
    private BigDecimal days;
    /** NATURAL 自然日 / WORKDAY 工作日 */
    private String dayMode;
    /** 天数计算告警（如未配置工作日历），无则为空 */
    private String warning;
}
