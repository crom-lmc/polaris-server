package com.laimengchao.sso.account;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 账户服务启动类。scanBasePackages 显式包含 common 包以启用全局异常处理。
 * 注意：本模块为微服务模板的「账户」服务；认证/网关等由其它模块承担（本模板未生成）。
 */
@SpringBootApplication(scanBasePackages = {
        "com.laimengchao.sso.account",
        "com.laimengchao.sso.common"
})
@MapperScan("com.laimengchao.sso.account.mapper")
public class SsoAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoAccountApplication.class, args);
    }
}
