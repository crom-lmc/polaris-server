package com.laimengchao.sso.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.laimengchao.sso.auth.config.AuthProperties;

/**
 * 认证服务入口。
 * <p>
 * 职责边界：只负责「把人认出来」并签发令牌，不持有账户表、不做账户 CRUD。
 * 凭据校验通过内部接口委托给 sso-account，权限码也由 sso-account 提供。
 */
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.auth", "com.laimengchao.sso.common"})
@EnableConfigurationProperties(AuthProperties.class)
public class SsoAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoAuthApplication.class, args);
    }
}
