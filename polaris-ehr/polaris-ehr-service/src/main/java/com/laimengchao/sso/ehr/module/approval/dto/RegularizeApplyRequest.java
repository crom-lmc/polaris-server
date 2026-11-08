package com.laimengchao.sso.ehr.module.approval.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 转正申请入参。
 * <p>
 * 全部字段可选：没填也能提交（老前端只按一个按钮时不会因为缺字段提交失败），
 * 但前端会做必填校验并提示，审批人看到的是填好的内容。
 */
@Data
public class RegularizeApplyRequest {

    /** 申请转正日期 yyyy-MM-dd */
    @Size(max = 10, message = "转正日期格式应为 yyyy-MM-dd")
    private String regularizeDate;

    /** 试用期工作自评 */
    @Size(max = 500, message = "试用期工作自评不能超过 500 字")
    private String selfReview;

    /** 试用期工作业绩与成果 */
    @Size(max = 1000, message = "试用期工作业绩不能超过 1000 字")
    private String workSummary;

    /** 转正申请理由 */
    @Size(max = 500, message = "转正申请理由不能超过 500 字")
    private String reason;

    /** 转正后的工作目标与计划 */
    @Size(max = 1000, message = "转正后工作目标不能超过 1000 字")
    private String nextPlan;
}
