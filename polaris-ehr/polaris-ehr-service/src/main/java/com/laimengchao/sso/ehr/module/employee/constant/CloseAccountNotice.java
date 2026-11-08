package com.laimengchao.sso.ehr.module.employee.constant;

/**
 * 离职 / 减员审批通过后的「关闭账号」待办约定。
 * <p>
 * 待办的 bizType 固定为 {@link #BIZ_TYPE}，bizId 存<b>员工ID</b>（不是审批实例号）；
 * HR 部门群发的待办只要有一个人完成了关闭动作，就按这对键把所有人的未读统一置读。
 */
public final class CloseAccountNotice {

    public static final String BIZ_TYPE = "ehr.close-account";

    private CloseAccountNotice() {
    }
}
