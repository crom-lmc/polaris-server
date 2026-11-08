package com.laimengchao.sso.account.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 新建账户请求。
 */
@Data
public class CreateAccountRequest {
    /** 3-32 位字母/数字/下划线 */
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String password;
    /** 关联角色ID列表 */
    private List<Long> roleIds;
}
