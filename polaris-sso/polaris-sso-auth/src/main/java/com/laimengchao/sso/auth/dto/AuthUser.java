package com.laimengchao.sso.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 账户身份快照。字段名与 sso-account 内部接口返回的 JSON 一一对应，
 * 两服务通过这个契约解耦（auth 不依赖 account 的实体/DTO 类）。
 */
@Data
public class AuthUser {

    private Long accountId;
    private String username;
    private String nickname;
    private boolean superAdmin;
    /** 权限码所属的应用编码；sso 表示平台自身 */
    private String appCode;
    /** 按钮级权限码（仅限 appCode 指定的应用域内） */
    private List<String> permissions;
    /** 可访问的应用编码（超管为全部启用应用），写入 JWT 的 apps claim */
    private List<String> apps;
}
