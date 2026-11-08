package com.laimengchao.sso.account.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级 / 接口级权限注解。标注在 Controller 方法上，由 {@link PermissionAspect} 在调用前校验。
 *
 * <p>示例：
 * <pre>
 *   @Permit("account:create")                 // 需要 account:create
 *   @Permit(value={"account:enable","account:disable"}, logical=Logical.ANY) // 任一即可
 * </pre>
 *
 * <p>超级管理员（角色 SUPER_ADMIN）自动放行。未登录访问带 @Permit 的方法返回 401。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Permit {

    /** 需要的权限码 */
    String[] value();

    /** ANY=拥有其一即可；ALL=必须全部拥有。默认 ANY。 */
    Logical logical() default Logical.ANY;
}
