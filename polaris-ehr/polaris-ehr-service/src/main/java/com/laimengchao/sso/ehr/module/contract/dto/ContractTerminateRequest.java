package com.laimengchao.sso.ehr.module.contract.dto;

import lombok.Data;

/** 终止合同申请。 */
@Data
public class ContractTerminateRequest {

    /** 终止原因，必填 */
    private String reason;
}
