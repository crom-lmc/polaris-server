package com.laimengchao.sso.account.dto.request;

import lombok.Data;

/**
 * 当前用户修改自己的密码。需要校验原密码，但不走登录失败锁定逻辑
 * （改密码场景下输错原密码几次就锁账户不合理）。
 */
@Data
public class ChangePasswordRequest {
    private String oldPassword;
    private String newPassword;
}
