package com.laimengchao.sso.forum.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.common.security.PermissionCodes;
import com.laimengchao.sso.common.security.SecurityUser;
import com.laimengchao.sso.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

/**
 * 按钮级权限切面，四步：未登录 401 → 令牌作用域必须匹配注解声明的应用（403）→ 超管旁路 → 权限码判定。
 *
 * <p>论坛双应用域：管理端接口对应 {@code forum-admin}，前台写操作对应 {@code forum}。
 * 作用域不匹配一律拒绝，前台令牌进不了管理端，管理端令牌也不会被前台码误判。
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(com.laimengchao.sso.forum.security.Permit)")
    public void check(JoinPoint joinPoint) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Permit permit = method.getAnnotation(Permit.class);
        String expectedApp = permit.app() == null || permit.app().isBlank()
                ? PermissionCodes.APP_FORUM_ADMIN : permit.app().trim();
        if (!expectedApp.equals(user.getAppCode())) {
            log.warn("跨应用访问论坛接口被拒：app={} 期望={} 账户={} 方法={}", user.getAppCode(), expectedApp,
                    user.getUsername(), joinPoint.getSignature().toShortString());
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该接口仅限 " + expectedApp + " 令牌访问（当前作用域：" + user.getAppCode() + "）");
        }
        if (user.isSuperAdmin()) {
            return;
        }
        Set<String> owned = user.getPermissions() == null ? Set.of() : user.getPermissions();
        boolean ok = switch (permit.logical()) {
            case ALL -> Arrays.stream(permit.value()).allMatch(owned::contains);
            case ANY -> Arrays.stream(permit.value()).anyMatch(owned::contains);
        };
        if (!ok) {
            log.warn("权限不足：账户={} 需要={} 拥有={}", user.getUsername(),
                    Arrays.toString(permit.value()), owned);
            throw new BizException(ErrorCode.FORBIDDEN, "无权限：" + String.join("/", permit.value()));
        }
    }
}
