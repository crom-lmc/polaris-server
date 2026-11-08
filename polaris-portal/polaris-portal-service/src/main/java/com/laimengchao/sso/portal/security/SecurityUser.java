package com.laimengchao.sso.portal.security;

import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 门户用户主体：没有密码、没有本地角色，身份完全来自统一账号中心
 * （sso-auth 签发、sso-gateway 验签后写入可信头）。主要供评论等需登录功能使用。
 */
@Data
public class SecurityUser implements UserDetails {

    /** SSO 账户 ID：评论作者等记它 */
    private Long accountId;
    private String username;
    private boolean superAdmin;
    /** 令牌作用域：只有 portal 作用域的令牌能用本服务的登录接口 */
    private String appCode;
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
        user.setAuthorities(user.getPermissions().stream()
                .map(code -> (GrantedAuthority) new SimpleGrantedAuthority("PERM_" + code))
                .collect(Collectors.toList()));
        return user;
    }

    public String getDisplayName() {
        return username;
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
