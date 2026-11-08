package com.laimengchao.sso.account.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 编辑账户请求（不含密码；改密走专门接口）。
 */
@Data
public class UpdateAccountRequest {
    private String nickname;
    private String email;
    private String phone;
    /** 关联角色ID列表（全量覆盖） */
    private List<Long> roleIds;
}
