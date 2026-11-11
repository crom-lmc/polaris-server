package com.laimengchao.sso.job;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 调度中心（端口 9008，独立库 polaris_job）。
 * <p>
 * 统一纳管各应用的定时任务：任务定义、执行器注册、执行日志、失败报警。
 * 管理端走网关可信头 + @Permit 按钮级权限；应用经 /internal/jobs/** 上报 Handler 清单与心跳。
 * <p>
 * J2 起内置调度内核：租约选主 + 1 秒扫描 + 乐观锁认领 + 派发到各应用的 /internal/jobs/run。
 */
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.job", "com.laimengchao.sso.common"})
@MapperScan("com.laimengchao.sso.job.module.**.mapper")
@ConfigurationPropertiesScan("com.laimengchao.sso.job")
@EnableScheduling
public class JobApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication.class, args);
    }
}
