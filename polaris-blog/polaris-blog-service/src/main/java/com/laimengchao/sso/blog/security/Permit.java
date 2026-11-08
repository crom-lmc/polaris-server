package com.laimengchao.sso.blog.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限注解。写在 Controller 方法上，由 {@link PermissionAspect} 在调用前校验。
 *
 * <pre>
 *   &#64;Permit("article:list")                          // 需要 article:list
 *   &#64;Permit({"article:edit", "article:publish"})        // 任一即可
 * </pre>
 *
 * <p>权限码由统一账号中心维护（应用 code = blog），因此给博客加一个按钮，
 * 只需在账号中心「权限管理」里加一个码、在方法上标注，不用改这张表之外的任何配置。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Permit {

    /** 需要的权限码 */
    String[] value();

    /** ANY=拥有其一即可；ALL=必须全部拥有。默认 ANY。 */
    Logical logical() default Logical.ANY;

    enum Logical {
        ANY, ALL
    }
}
