package com.laimengchao.sso.forum.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限注解，权限码由统一账号中心维护（应用 code = forum-admin）。
 *
 * <pre>
 *   &#64;Permit("forum:content:view")                            // 需要 forum:content:view
 *   &#64;Permit(value = {"forum:content:delete", "forum:content:pin"})  // 任一即可
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Permit {

    String[] value();

    Logical logical() default Logical.ANY;

    enum Logical {
        ANY, ALL
    }
}
