package com.laimengchao.sso.blog.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前登录主体工具类。
 * <p>
 * 和旧版本同名，但语义变了：{@link #currentUserId()} 返回的是 <b>SSO 账户 ID</b>，
 * 不再是 t_user.id —— 博客自己的用户表已废弃，作者/上传人/操作人统一记账户 ID。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<SecurityUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** 当前 SSO 账户 ID，未登录返回 null */
    public static Long currentUserId() {
        return currentUser().map(SecurityUser::getAccountId).orElse(null);
    }

    /** 当前登录名，未登录返回 null */
    public static String currentUsername() {
        return currentUser().map(SecurityUser::getUsername).orElse(null);
    }

    public static boolean isSuperAdmin() {
        return currentUser().map(SecurityUser::isSuperAdmin).orElse(false);
    }

    public static boolean isAdmin() {
        return currentUser().map(SecurityUser::isAdmin).orElse(false);
    }
}
