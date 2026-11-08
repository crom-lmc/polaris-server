package com.laimengchao.sso.util;

import java.util.Collections;
import java.util.Set;

/**
 * 登录上下文（线程级）。由 sso-account 的 LoginContextInterceptor 在请求进入时填充，
 * 请求结束后清除。{@code @Permit} 切面与业务代码通过它获取「当前账户」与「权限码集合」。
 *
 * <p>设计要点：superAdmin=true 时跳过一切 {@code @Permit} 校验（超级管理员拥有全部权限）。
 */
public final class AccountContext {

    private static final ThreadLocal<AccountInfo> HOLDER = new ThreadLocal<>();

    private AccountContext() {
    }

    public static void set(AccountInfo info) {
        HOLDER.set(info);
    }

    public static AccountInfo get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Long getAccountId() {
        AccountInfo info = HOLDER.get();
        return info == null ? null : info.getAccountId();
    }

    public static String getUsername() {
        AccountInfo info = HOLDER.get();
        return info == null ? null : info.getUsername();
    }

    public static Set<String> getPermissions() {
        AccountInfo info = HOLDER.get();
        return info == null ? Collections.emptySet() : info.getPermissions();
    }

    public static boolean isSuperAdmin() {
        AccountInfo info = HOLDER.get();
        return info != null && info.isSuperAdmin();
    }

    /** 当前令牌所属应用（权限码作用域）；null 表示未声明，按平台自身处理 */
    public static String getAppCode() {
        AccountInfo info = HOLDER.get();
        return info == null ? null : info.getAppCode();
    }

    /** 当前账户快照 */
    public static class AccountInfo {
        private Long accountId;
        private String username;
        private Set<String> permissions;
        private boolean superAdmin;
        /** 令牌作用域：sso 表示平台自身，其余是业务应用编码 */
        private String appCode;

        public AccountInfo() {
        }

        public AccountInfo(Long accountId, String username, Set<String> permissions, boolean superAdmin) {
            this(accountId, username, permissions, superAdmin, null);
        }

        public AccountInfo(Long accountId, String username, Set<String> permissions,
                           boolean superAdmin, String appCode) {
            this.accountId = accountId;
            this.username = username;
            this.permissions = permissions;
            this.superAdmin = superAdmin;
            this.appCode = appCode;
        }

        public String getAppCode() {
            return appCode;
        }

        public void setAppCode(String appCode) {
            this.appCode = appCode;
        }

        public Long getAccountId() {
            return accountId;
        }

        public void setAccountId(Long accountId) {
            this.accountId = accountId;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Set<String> getPermissions() {
            return permissions;
        }

        public void setPermissions(Set<String> permissions) {
            this.permissions = permissions;
        }

        public boolean isSuperAdmin() {
            return superAdmin;
        }

        public void setSuperAdmin(boolean superAdmin) {
            this.superAdmin = superAdmin;
        }
    }
}
