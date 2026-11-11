package com.laimengchao.sso.cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * CMS 内容管理服务（cms，端口 9004）。
 * <p>
 * 与 blog-admin 同一套接入模板：身份由 sso-gateway 验签后经 X-Account-* 可信头传入，
 * 本服务不认证、不签发 JWT；按钮级权限由 {@code security.Permit} + 统一账号中心的权限码决定。
 * 组件扫描限定在自己的包：sso-common 里有同名异常处理器，扫进来会打架。
 */
@ConfigurationPropertiesScan("com.laimengchao.sso.cms")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.cms", "com.laimengchao.sso.common"})
public class CmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CmsApplication.class, args);
    }
}
