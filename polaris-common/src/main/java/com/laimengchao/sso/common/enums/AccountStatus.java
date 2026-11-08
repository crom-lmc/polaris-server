package com.laimengchao.sso.common.enums;

import lombok.Getter;

/**
 * 账户状态。注意区分：
 * - DISABLED 是管理员手动禁用（只能由「启用」按钮解除）
 * - LOCKED 是登录失败次数超限自动锁定（只能由「解锁」按钮解除）
 */
@Getter
public enum AccountStatus {

    NORMAL(1, "正常"),
    DISABLED(2, "已禁用"),
    LOCKED(3, "已锁定"),
    INACTIVE(4, "未激活");

    private final int code;
    private final String desc;

    AccountStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AccountStatus of(int code) {
        for (AccountStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return NORMAL;
    }
}
