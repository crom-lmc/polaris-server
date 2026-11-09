package com.laimengchao.sso.common.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一后台用户主体：没有密码、没有本地角色，身份完全来自统一账号中心
 * （sso-auth 签发、sso-gateway 验签后写入可信头）。
 * <p>各业务服务（ehr / settings / portal / cms / blog）共用本类，避免各自复制一份。
 */
@Data
public class SecurityUser implements UserDetails {

    /** SSO 账户 ID：作者/上传人/审核人都记它 */
    private Long accountId;

    private String username;

    /** 是否为统一账号中心的超级管理员：可越过所有按钮级权限 */
    private boolean superAdmin;

    /** 令牌作用域：只有对应应用作用域的令牌能用本服务的管理接口 */
    private String appCode;

    /** 按钮级权限码（来自令牌） */
    private Set<String> permissions = Set.of();

    private Collection<? extends GrantedAuthority> authorities = List.of();

    public static SecurityUser of(Long accountId, String username, boolean superAdmin,
                                  String appCode, Set<String> permissions) {
        SecurityUser user = new SecurityUser();
        user.setAccountId(accountId);
        user.setUsername(username);
        user.setSuperAdmin(superAdmin);
        user.setAppCode(appCode);
        user.setPermissions(permissions == null ? Set.of() : permissions);
        // 角色由权限推导，仅用于兼容 Spring Security 表达式；真正判定在 @Permit 上
        user.setAuthorities(user.getPermissions().stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority("PERM_" + code))
                .collect(Collectors.toList()));
        return user;
    }

    /** 头里只有 ASCII 登录名，中文昵称拿不到，统一用登录名 */
    public String getDisplayName() {
        return username;
    }

    /** 兼容内容类应用的昵称字段：统一回落到登录名 */
    public String getNickname() {
        return username;
    }

    /** 可信头不带邮箱 */
    public String getEmail() {
        return null;
    }

    /** 可信头不带头像 */
    public String getAvatar() {
        return null;
    }

    /**
     * 后台身份：超管，或持有所属应用作用域的令牌。
     * 具体能做什么仍由 {@link #permissions} 逐项决定。
     */
    public boolean isAdmin() {
        return superAdmin || (appCode != null && !appCode.isBlank());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
