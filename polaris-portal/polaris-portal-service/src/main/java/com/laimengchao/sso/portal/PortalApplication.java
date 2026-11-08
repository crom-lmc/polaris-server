package com.laimengchao.sso.portal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 内容门户服务（portal，端口 9005）。
 * <p>
 * 与 cms/blog-admin 同一套接入模板：身份由 sso-gateway 验签后经 X-Account-* 可信头传入，
 * 本服务不认证、不签发 JWT；单点换票由本服务持 PORTAL_APP_SECRET 向 sso-auth 完成。
 * 门户公开内容直接走 cms 的 /api/cms/public/**，这里只做登录态与后续扩展（评论等）。
 * 组件扫描限定在自己的包：sso-common 里有同名异常处理器，扫进来会打架。
 */
@ConfigurationPropertiesScan("com.laimengchao.sso.portal")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.portal", "com.laimengchao.sso.common"})
public class PortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortalApplication.class, args);
    }
}
