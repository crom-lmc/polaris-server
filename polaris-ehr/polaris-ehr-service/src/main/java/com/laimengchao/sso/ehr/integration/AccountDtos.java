package com.laimengchao.sso.ehr.integration;

import lombok.Data;

import java.util.List;

/**
 * 跨服务开户的请求/响应模型。
 * <p>
 * 为什么由 account 侧决定登录名与初始密码：唯一性校验、密码策略、失败计数都在账户域，
 * 放到 EHR 侧再做一遍等于把账户规则复制两份，迟早走偏。
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** 开户结果 */
    @Data
    public static class OpenResult {
        private Long accountId;
        private String username;
        /** 仅在本次新建账号时返回明文 */
        private String initialPassword;
        private boolean created;
        private List<String> apps;
        private List<String> roles;
    }
}
