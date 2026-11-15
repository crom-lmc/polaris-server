package com.laimengchao.sso.common.security;

/**
 * 数据权限核心判断工具。零依赖：不需要 Spring、不需要 MyBatis、不需要 Redis。
 * 所有模块都能直接调用。
 *
 * <p>规则：一条数据行归属的 {@code orgCode}（如 XC000100010001）必须以
 * 当前账号数据权限根 {@code scopeCode}（如 XC00010001）开头才算在范围内。
 */
public final class DataScopeHelper {

    private DataScopeHelper() {}

    /**
     * 判断一条数据行归属的 orgCode 是否在 scopeCode 权限根之下。
     *
     * @param scopeCode 数据权限根组织编码（账号级配置），空串或 null → 返回 false
     * @param orgCode   数据行归属的组织编码，null → 返回 false
     */
    /**
     * scopeCode 支持逗号分隔多值，任意一个匹配 orgCode 前缀即放行。
     * 单值时行为与旧版完全一致。
     */
    public static boolean canAccessOrg(String scopeCode, String orgCode) {
        if (scopeCode == null || orgCode == null) return false;
        String[] roots = scopeCode.split(",");
        for (String root : roots) {
            root = root.trim();
            if (root.isEmpty()) continue;
            if (orgCode.startsWith(root)) return true;
        }
        return false;
    }
}
