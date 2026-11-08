package com.laimengchao.sso.blog.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 博客后台的用户主体。
 * <p>
 * 与旧的 {@code SecurityUser} 的关键差别：这里<b>没有密码</b>，也不再有博客自己的
 * userId/role —— 身份由统一账号中心（sso-auth 签发、sso-gateway 验签）确定，
 * 本类只是把网关注入的可信头翻译成 Spring Security 认得的形状。
 * <p>
 * {@code accountId} 即 SSO 账户 ID，媒体上传人、操作日志的操作者都记它。
 */
@Data
public class SecurityUser implements UserDetails {

    /** SSO 账户 ID（原 t_user.id 的替代者） */
    private Long accountId;

    private String username;

    /** 是否为统一账号中心的超级管理员：可越过所有按钮级权限 */
    private boolean superAdmin;

    /** 令牌作用域：sso / blog。只有 blog 作用域的令牌能用本服务的接口 */
    private String appCode;

    /** 按钮级权限码（来自令牌，blog 应用域内的码） */
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
        // 角色是由权限推导出来的，仅用于兼容 Spring Security 的表达式；
        // 真正的判定在 @Permit 上，颗粒到按钮
        user.setAuthorities(permissions == null ? List.of()
                : permissions.stream()
                .map(code -> new SimpleGrantedAuthority("PERM_" + code))
                .collect(Collectors.toList()));
        return user;
    }

    /**
     * 后台身份。
     * <p>
     * 统一账号中心没有「博客角色」这个概念，能拿到 blog 作用域令牌就说明有后台入口权限；
     * 具体能做哪些操作由 {@link #permissions} 逐项决定。
     */
    public boolean isAdmin() {
        return superAdmin || "blog".equals(appCode);
    }

    /** 昵称在可信头里没有（HTTP 头只能放 ASCII，中文会乱码），回落到登录名 */
    public String getNickname() {
        return username;
    }

    public String getEmail() {
        return null;
    }

    public String getAvatar() {
        return null;
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
