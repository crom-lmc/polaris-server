package com.laimengchao.sso.forum.security;

import com.laimengchao.sso.common.security.PermissionCodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限注解，权限码由统一账号中心维护，常量见 {@link PermissionCodes}。
 *
 * <p>论坛有双应用域：管理端接口默认要求 {@code forum-admin} 令牌，
 * 前台接口显式声明 {@code app = PermissionCodes.APP_FORUM}。令牌作用域不匹配时直接 403，
 * 这样前台令牌即使拿到同名权限码也进不了管理端。
 *
 * <pre>
 *   &#64;Permit(PermissionCodes.FORUM_CONTENT_VIEW)                        // 管理端
 *   &#64;Permit({PermissionCodes.FORUM_CONTENT_DELETE, PermissionCodes.FORUM_CONTENT_PIN})  // 管理端，任一即可
 *   &#64;Permit(value = PermissionCodes.FORUM_POST_CREATE,
 *           app = PermissionCodes.APP_FORUM)                            // 前台写操作
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Permit {

    String[] value();

    /** 允许访问的令牌应用域，默认论坛管理端 */
    String app() default PermissionCodes.APP_FORUM_ADMIN;

    Logical logical() default Logical.ANY;

    enum Logical {
        ANY, ALL
    }
}
