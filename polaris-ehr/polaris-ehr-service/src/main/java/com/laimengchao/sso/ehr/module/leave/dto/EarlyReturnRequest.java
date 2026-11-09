package com.laimengchao.sso.ehr.module.leave.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 提前返岗销假：returnDate 为实际返岗日期（yyyy-MM-dd） */
@Data
public class EarlyReturnRequest {

    @NotBlank(message = "返岗日期必填")
    private String returnDate;

    private String note;
}
