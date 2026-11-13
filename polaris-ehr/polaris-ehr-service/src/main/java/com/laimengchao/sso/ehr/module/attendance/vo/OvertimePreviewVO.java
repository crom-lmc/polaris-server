package com.laimengchao.sso.ehr.module.attendance.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 加班申请试算（提交前预览）。 */
@Data
public class OvertimePreviewVO {

    /** NORMAL / HOLIDAY / RESTDAY */
    private String type;
    private String typeName;
    /** 区间总分钟（未扣除工作时段） */
    private Integer totalMinutes;
    /** 有效加班分钟（扣除工作时段重叠后） */
    private Integer overtimeMinutes;
    /** 有效加班小时（0.5h 粒度向下取整，可能为 0 表示扣除后无有效加班） */
    private BigDecimal hours;
    /** 是否有对应日期的打卡记录 */
    private Boolean punchExists;
    /** 提示："正常" / "扣除工作时段 X 分钟" / "请调整时段" */
    private String tip;
}
