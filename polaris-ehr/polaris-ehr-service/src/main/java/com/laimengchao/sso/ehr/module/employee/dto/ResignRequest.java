package com.laimengchao.sso.ehr.module.employee.dto;

import lombok.Data;

/** 办理离职：日期与原因留痕，并默认停用统一账号 */
@Data
public class ResignRequest {

    /** 离职日期 yyyy-MM-dd，留空取今天 */
    private String resignDate;
    /** 离职原因（选填） */
    private String reason;
    /** 是否同时停用统一账号，默认 true；停用后各系统立即无法登录 */
    private Boolean disableAccount;
}
