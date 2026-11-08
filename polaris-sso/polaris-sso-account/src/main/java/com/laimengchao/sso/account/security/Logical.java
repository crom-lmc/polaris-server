package com.laimengchao.sso.account.security;

/**
 * @Permit 多权限码组合逻辑。
 */
public enum Logical {
    /** 拥有其一即可 */
    ANY,
    /** 必须全部拥有 */
    ALL
}
