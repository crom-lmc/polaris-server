package com.laimengchao.sso.openapi.security.admin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端按钮级权限注解，权限码由统一账号中心维护（应用 code = openapi）。
 * 风格与 settings/cms 等服务的 Permit 完全一致；仅作用于 /api/open/admin/**。
 *
 * <pre>
 *   &#64;Permit("openapi:channel:list")
 *   &#64;Permit(value = {"openapi:channel:edit", "openapi:channel:credential"})  // 任一即可
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
