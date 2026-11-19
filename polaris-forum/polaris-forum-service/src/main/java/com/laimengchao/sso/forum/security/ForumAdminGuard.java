package com.laimengchao.sso.forum.security;

import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.common.security.SecurityUser;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 论坛后台身份判定，只用于「下载他人附件」这类资源归属旁路。
 * 管理端接口的权限卡点统一走 {@link Permit} 注解 + {@link PermissionAspect}，不再在这里判权。
 */
@Component
public class ForumAdminGuard {

    /** 8 个业务菜单的查看码，拥有任一即视为论坛后台人员 */
    private static final Set<String> ADMIN_PERMISSIONS = Set.of(PermissionCodes.FORUM_CONTENT_VIEW,
            PermissionCodes.FORUM_COMMENT_VIEW, PermissionCodes.FORUM_SENSITIVE_VIEW,
            PermissionCodes.FORUM_EXCHANGE_VIEW, PermissionCodes.FORUM_BADGE_VIEW,
            PermissionCodes.FORUM_TAXONOMY_VIEW, PermissionCodes.FORUM_REPORT_VIEW,
            PermissionCodes.FORUM_SITE_VIEW);

    public boolean isForumAdmin(SecurityUser user) {
        return user != null && (user.isSuperAdmin() || user.getPermissions() != null
                && user.getPermissions().stream().anyMatch(ADMIN_PERMISSIONS::contains));
    }

}
