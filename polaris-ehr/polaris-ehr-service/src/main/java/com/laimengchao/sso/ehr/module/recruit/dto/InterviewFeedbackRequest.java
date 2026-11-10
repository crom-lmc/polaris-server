package com.laimengchao.sso.ehr.module.recruit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 面试官填写面试反馈 */
@Data
public class InterviewFeedbackRequest {

    @Min(value = 1, message = "评分范围 1-5")
    @Max(value = 5, message = "评分范围 1-5")
    private Integer score;

    @NotBlank(message = "请填写面试反馈")
    private String feedback;

    /** 本轮结论：PASS 通过 / FAIL 不通过（不通过直接淘汰候选人） */
    @NotBlank(message = "请选择本轮面试结果")
    private String result;
}
