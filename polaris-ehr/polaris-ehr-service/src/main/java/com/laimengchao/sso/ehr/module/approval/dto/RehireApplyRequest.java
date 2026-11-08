package com.laimengchao.sso.ehr.module.approval.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 复职申请入参 */
@Data
public class RehireApplyRequest {

    /** 拟复职日期 yyyy-MM-dd，为空默认取当天 */
    private String rehireDate;

    @Size(max = 255, message = "复职原因不能超过 255 字")
    private String reason;

    /** 是否需要同时启用统一账号，默认 true */
    private Boolean enableAccount;
}
