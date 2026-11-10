package com.laimengchao.sso.ehr.module.employee.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 司龄核定入参：HR 按社保 / 离职证明核对后提交 */
@Data
public class SeniorityConfirmRequest {

    /** 核定生效日，为空按今天 */
    private LocalDate effectiveDate;

    @NotNull(message = "司龄起算日必填")
    private LocalDate seniorityStartDate;

    @NotNull(message = "首次参加工作日期必填")
    private LocalDate firstWorkDate;

    /** 前司累计年限；传了 segments 时以分段加总为准，本字段忽略 */
    private BigDecimal priorYears;

    /** 累计工龄；为空时按「前司年限 + 本单位司龄」自动计算 */
    private BigDecimal totalYears;

    /** SOCIAL_SECURITY / MANUAL / FIRST_HIRE，默认 MANUAL */
    private String source;

    /** 社保 / 离职证明附件ID（整单证明，兼容旧数据；分段核定后以每段附件为准） */
    private Long proofFileId;

    private String remark;

    /** 前司多段工作经历：每段起止日期 + 证明附件，后端合并重叠区间后自动加总 */
    @Valid
    private List<Segment> segments;

    @Data
    public static class Segment {

        /** 单位名称（选填） */
        private String company;

        @NotNull(message = "工作段开始日期必填")
        private LocalDate startDate;

        @NotNull(message = "工作段结束日期必填")
        private LocalDate endDate;

        /** 该段工作证明附件ID（社保/离职证明），每段必传 */
        @NotNull(message = "每段工作经历都要上传证明附件")
        private Long proofFileId;

        private String proofFileName;

        private String remark;
    }
}
