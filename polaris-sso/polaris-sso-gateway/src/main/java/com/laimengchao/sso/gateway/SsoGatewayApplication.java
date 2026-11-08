package com.laimengchao.sso.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import com.laimengchao.sso.gateway.config.GatewayProperties;

/**
 * 网关入口。WebFlux 技术栈，只做三件事：路由、验签、可信头注入。
 * <p>
 * 刻意不扫描 sso-common 包：common 里有基于 Servlet MVC 的全局异常处理，
 * 在 WebFlux 环境注册会出现两套异常体系，网关自己的错误响应保持在 filter 内。
 */
@SpringBootApplication(scanBasePackages = "com.laimengchao.sso.gateway")
@EnableConfigurationProperties(GatewayProperties.class)
@EnableScheduling
public class SsoGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(SsoGatewayApplication.class, args);
    }
}
