package com.laimengchao.sso.ehr.module.leave.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 额度调整申请：delta 正数增加、负数减少 */
@Data
public class QuotaAdjustRequest {

    @NotNull(message = "调整天数必填")
    private BigDecimal delta;

    @NotBlank(message = "调整原因必填")
    private String reason;
}
