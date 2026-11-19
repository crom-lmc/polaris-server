package com.laimengchao.sso.forum.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ForumAdminGuard {

    private static final Set<String> ADMIN_PERMISSIONS = Set.of("forum:admin", "forum:content", "forum:sensitive", "forum:exchange",
            "forum:badge", "forum:taxonomy", "forum:report", "forum:site");

    public SecurityUser require(String permission) {
        SecurityUser user = SecurityUtils.currentUser().orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED));
        if (!user.isSuperAdmin() && (user.getPermissions() == null || !user.getPermissions().contains(permission))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return user;
    }

    public boolean isForumAdmin(SecurityUser user) {
        return user != null && (user.isSuperAdmin() || user.getPermissions() != null
                && user.getPermissions().stream().anyMatch(ADMIN_PERMISSIONS::contains));
    }

}
