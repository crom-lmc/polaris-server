package com.laimengchao.sso.job.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限注解，权限码由统一账号中心维护（应用 code = job）。
 *
 * <pre>
 *   &#64;Permit("job:view")                             // 需要 job:view
 *   &#64;Permit(value = {"job:manage", "job:run"})       // 任一即可
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
