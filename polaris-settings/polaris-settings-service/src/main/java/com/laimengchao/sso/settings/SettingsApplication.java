package com.laimengchao.sso.settings;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 配置中心（端口 9007，独立库 settings）。
 * <p>
 * 收口全平台的枚举型基础信息（请假类型/职位/性别/离职原因分类等）：
 * 管理端走网关可信头 + @Permit 按钮级权限；其他服务经 /internal/dicts/** 拉取（X-Internal-Token）。
 * <p>
 * 同时承载审批中心：模板配置、在途实例、待办任务都在这里。业务模块不再保存审批过程，
 * 只通过 /internal/approval/** 发起审批并接收终审回调（回调失败由调度中心定时重试）。
 */
@SpringBootApplication(scanBasePackages = {"com.laimengchao.sso.settings", "com.laimengchao.sso.common"})
@MapperScan("com.laimengchao.sso.settings.module.**.mapper")
public class SettingsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettingsApplication.class, args);
    }
}
