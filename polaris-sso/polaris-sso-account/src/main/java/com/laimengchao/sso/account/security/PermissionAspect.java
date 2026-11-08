package com.laimengchao.sso.account.security;

import com.laimengchao.sso.account.constant.PermissionConstants;
import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.util.AccountContext;
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
 * 按钮级权限切面：在标注 @Permit 的方法执行前校验当前账户是否拥有所需权限码。
 * 这是「权限控制到按钮级」的核心——每个按钮对应一个接口，接口方法上标注对应的权限码。
 *
 * <p><b>应用边界</b>：本服务的所有 @Permit 接口都属于平台自身（sso）。业务应用的令牌
 * 即便「碰巧」持有同名权限码（它完全可以在自己应用里也定义 account:list），也不得通过——
 * 否则接入一个应用就等于开了一道通往管理台的后门。超管旁路同理，只在平台令牌上生效。
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    @Before("@annotation(com.laimengchao.sso.account.security.Permit)")
    public void check(JoinPoint joinPoint) {
        // 1. 未登录
        if (AccountContext.getAccountId() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        // 2. 应用边界：非平台令牌一律拒绝，先于超管旁路
        String appCode = AccountContext.getAppCode();
        if (appCode != null && !appCode.isBlank() && !PermissionConstants.PLATFORM_APP.equals(appCode)) {
            log.warn("跨应用访问平台接口被拒：app={} 账户={} path={}", appCode,
                    AccountContext.getUsername(), joinPoint.getSignature().toShortString());
            throw new BizException(ErrorCode.FORBIDDEN, "该接口仅限平台令牌访问（当前令牌作用域：" + appCode + "）");
        }
        // 3. 超级管理员放行
        if (AccountContext.isSuperAdmin()) {
            return;
        }
        // 3. 读取注解要求的权限码
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Permit permit = method.getAnnotation(Permit.class);
        Set<String> owned = AccountContext.getPermissions();

        boolean ok = switch (permit.logical()) {
            case ALL -> Arrays.stream(permit.value()).allMatch(owned::contains);
            case ANY -> Arrays.stream(permit.value()).anyMatch(owned::contains);
        };
        if (!ok) {
            log.warn("权限不足：账户={} 需要={} 拥有={}", AccountContext.getUsername(),
                    Arrays.toString(permit.value()), owned);
            throw new BizException(ErrorCode.FORBIDDEN, "无权限：" + String.join("/", permit.value()));
        }
    }
}
