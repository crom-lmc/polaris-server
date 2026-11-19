package com.laimengchao.sso.forum;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 独立论坛服务：只消费网关注入的统一账号身份，不同步组织架构。 */
@MapperScan("com.laimengchao.sso.forum.module.**.mapper")
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.forum", "com.laimengchao.sso.common"})
public class ForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForumApplication.class, args);
    }
}
