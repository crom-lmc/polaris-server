package com.laimengchao.sso.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前登录主体工具类；各业务服务共用。
 * <p>{@link #currentUserId()} 返回统一账号中心账户 ID（不是各应用本地用户 ID）。
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

    public static Long currentUserId() {
        return currentUser().map(SecurityUser::getAccountId).orElse(null);
    }

    /** 当前登录名（Optional，配合 .orElse 兜底默认值） */
    public static Optional<String> currentUsername() {
        return currentUser().map(SecurityUser::getUsername);
    }

    public static boolean isSuperAdmin() {
        return currentUser().map(SecurityUser::isSuperAdmin).orElse(false);
    }

    public static boolean isAdmin() {
        return currentUser().map(SecurityUser::isAdmin).orElse(false);
    }
}
