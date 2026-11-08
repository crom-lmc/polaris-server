package com.laimengchao.sso.blog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 博客管理端服务（blog-admin，端口 9003）。
 * <p>
 * 与原来的单体博客后端的区别只有一处：<b>身份不再由本服务认证</b>。
 * 请求经 sso-gateway 验签后，身份通过 X-Account-* 可信头传进来，
 * 本服务只负责把它们翻译成 SecurityContext 与权限判定（见 {@code security} 包）。
 * <p>
 * 组件扫描范围限定在自己这个包：sso-common 里也有一个 GlobalExceptionHandler，
 * 扫进来会和博客自己的异常处理器打架。
 */
@EnableScheduling
@ConfigurationPropertiesScan("com.laimengchao.sso.blog")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.blog", "com.laimengchao.sso.common"})
public class BlogAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogAdminApplication.class, args);
    }
}
