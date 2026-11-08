package com.laimengchao.sso.cms.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限注解，权限码由统一账号中心维护（应用 code = cms）。
 *
 * <pre>
 *   &#64;Permit("article:edit")                    // 需要 article:edit
 *   &#64;Permit(value = {"article:publish", "article:audit"})  // 任一即可
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
