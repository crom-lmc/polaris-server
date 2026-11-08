package com.laimengchao.sso.ehr.module.employee.dto;

import lombok.Data;

/** 复职：清空离职日期/原因，并默认重新启用统一账号 */
@Data
public class ReinstateRequest {

    /** 是否启用统一账号，默认 true（若账号在账号中心被单独禁用，这里也会恢复） */
    private Boolean enableAccount;
    /** 复职备注（选填，追加到档案备注前） */
    private String remark;
}
