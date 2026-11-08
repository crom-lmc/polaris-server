package com.laimengchao.sso.account.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 登录结果（也是服务间身份快照契约）。
 * 前端据此拿到权限码集合与应用清单：权限码控按钮显隐，应用清单控「能进哪些系统」。
 *
 * <p>{@code permissions} 是**某个应用范围内**的权限码，不是全量——由 {@code appCode} 决定。
 * 平台管理台登录时 appCode=sso；业务应用通过 ticket 换到的令牌 appCode 是自己的 code。
 */
@Data
public class LoginResult {
    private Long accountId;
    private String username;
    private String nickname;
    private boolean superAdmin;
    /** 权限码所属的应用。sso 表示平台自身 */
    private String appCode;
    /** 当前账户在该应用内的权限码（按钮级） */
    private List<String> permissions;
    /** 可访问的应用编码（超管为全部启用应用），写入 JWT 的 apps claim */
    private List<String> apps;
}
