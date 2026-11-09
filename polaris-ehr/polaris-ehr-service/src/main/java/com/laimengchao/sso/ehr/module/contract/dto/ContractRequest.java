package com.laimengchao.sso.ehr.module.contract.dto;

import lombok.Data;

import java.time.LocalDate;

/** 新增 / 编辑合同的表单。 */
@Data
public class ContractRequest {

    private String contractNo;
    /** FIXED / UNFIXED / INTERN / LABOR */
    private String contractType;
    private LocalDate startDate;
    /** 无固定期限合同可为空 */
    private LocalDate endDate;
    private LocalDate signDate;
    private Long fileId;
    private String remark;
}
