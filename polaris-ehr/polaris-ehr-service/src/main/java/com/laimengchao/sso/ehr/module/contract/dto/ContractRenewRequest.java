package com.laimengchao.sso.ehr.module.contract.dto;

import lombok.Data;

import java.time.LocalDate;

/** 续签申请：新合同起止日期 + 续签说明。 */
@Data
public class ContractRenewRequest {

    private String contractNo;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate signDate;
    private Long fileId;
    private String remark;
    /** 续签原因，随审批单展示 */
    private String reason;
}
