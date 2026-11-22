package com.laimengchao.sso.openapi;

import com.laimengchao.sso.openapi.module.integration.EhrInternalApi;
import com.laimengchao.sso.openapi.module.integration.SettingsInternalApi;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 接口开放平台服务：第三方唯一对接点。
 * <p>
 * 权限与凭证的单一来源在统一登录中心（sso-account），本服务只做验签、映射与转发，
 * 自身库仅存开放 API 目录与调用日志。
 */
@MapperScan("com.laimengchao.sso.openapi.module.**.mapper")
@EnableFeignClients(clients = {SettingsInternalApi.class, EhrInternalApi.class})
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.openapi", "com.laimengchao.sso.common"})
public class OpenapiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenapiApplication.class, args);
    }
}
