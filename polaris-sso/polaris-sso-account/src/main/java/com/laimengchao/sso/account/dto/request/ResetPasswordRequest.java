package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 重置密码请求。管理员代重置，默认强制下次登录改密。
 */
@Data
public class ResetPasswordRequest {
    private String newPassword;
    /** 是否强制下次登录改密，默认 true */
    private Boolean forceChange;
}
