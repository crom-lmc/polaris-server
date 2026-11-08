package com.laimengchao.sso.settings.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.settings.common.SettingsErrorCode;
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
 * 按钮级权限切面，四步：未登录 401 → 令牌作用域必须是 settings（403）→ 超管旁路 → 权限码判定。
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    /** 与 sso_application.code 一致 */
    public static final String APP_CODE = "settings";

    @Before("@annotation(com.laimengchao.sso.settings.security.Permit)")
    public void check(JoinPoint joinPoint) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!APP_CODE.equals(user.getAppCode())) {
            log.warn("跨应用访问 settings 接口被拒：app={} 账户={} 方法={}", user.getAppCode(),
                    user.getUsername(), joinPoint.getSignature().toShortString());
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该接口仅限配置中心令牌访问（当前作用域：" + user.getAppCode() + "）");
        }
        if (user.isSuperAdmin()) {
            return;
        }
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Permit permit = method.getAnnotation(Permit.class);
        Set<String> owned = user.getPermissions();
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
