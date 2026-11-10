package com.laimengchao.sso.ehr.module.recruit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/** HR 新增一条候选人沟通记录 */
@Data
public class CandidateCommRequest {

    /** OFFER_TALK / PHONE / OTHER */
    private String type;

    @NotBlank(message = "请填写沟通内容")
    private String content;

    private BigDecimal expectedSalary;

    /** 沟通时间：yyyy-MM-dd HH:mm(:ss)，为空取当前时间 */
    private String occurredAt;
}
