package com.laimengchao.sso.account.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 服务间启停账号（EHR 离职/复职联动） */
@Data
public class InternalAccountStatusRequest {

    @NotNull(message = "enabled 必填")
    private Boolean enabled;
}
