package com.laimengchao.sso.blog.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.blog.common.BlogErrorCode;
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
 * 按钮级权限切面，执行顺序刻意分四步：
 * <ol>
 *   <li><b>未登录</b>：401</li>
 *   <li><b>令牌作用域</b>：必须是 blog。管理台的 sso 令牌、其他应用的令牌一律 403 ——
 *       没有这一步，接进来一个应用就等于开了一道通往博客后台的后门</li>
 *   <li><b>超级管理员</b>：越过所有按钮码（旁路放在作用域判定之后）</li>
 *   <li><b>权限码</b>：按 {@link Permit#logical()} 判定</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    /** 本服务的应用编码，与 sso_application.code 一致 */
    public static final String APP_CODE = "blog";

    @Before("@annotation(com.laimengchao.sso.blog.security.Permit)")
    public void check(JoinPoint joinPoint) {
        SecurityUser user = SecurityUtils.currentUser().orElse(null);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!APP_CODE.equals(user.getAppCode())) {
            log.warn("跨应用访问博客接口被拒：app={} 账户={} 方法={}", user.getAppCode(),
                    user.getUsername(), joinPoint.getSignature().toShortString());
            throw new BizException(ErrorCode.FORBIDDEN,
                    "该接口仅限博客后台令牌访问（当前作用域：" + user.getAppCode() + "）");
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
