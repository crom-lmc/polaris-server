package com.laimengchao.sso.common.enums;

import lombok.Getter;

/**
 * 权限类型：
 * - MENU：菜单/页面级（控制能否进入某页面）
 * - BUTTON：按钮/操作级（控制页面内某个按钮/接口是否可用）—— 即「按钮级权限」
 */
@Getter
public enum PermissionType {

    MENU(1, "菜单"),
    BUTTON(2, "按钮");

    private final int code;
    private final String desc;

    PermissionType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PermissionType of(int code) {
        for (PermissionType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        return BUTTON;
    }
}
