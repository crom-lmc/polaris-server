package com.laimengchao.sso.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 账户身份快照（sso-account 的 /internal/accounts/authenticate 与 /{id}/principal 出参）。
 * 与 auth 侧的 AuthUser 字段一一对应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AccountUser {

    private Long accountId;
    private String username;
    private String nickname;
    private boolean superAdmin;
    /** 权限码所属的应用编码；sso 表示平台自身 */
    private String appCode;
    /** 按钮级权限码（仅限 appCode 指定的应用域内） */
    private List<String> permissions;
    /** 可访问的应用编码，写入 JWT 的 apps claim */
    private List<String> apps;
}
