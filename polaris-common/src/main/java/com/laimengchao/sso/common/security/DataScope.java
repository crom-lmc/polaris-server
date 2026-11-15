package com.laimengchao.sso.common.security;

/**
 * 数据权限查询接口。各模块各自实现：
 * <ul>
 *   <li>polaris-sso-account：直接查本库 {@code sso_account.data_scope_org_code}</li>
 *   <li>polaris-ehr / polaris-cms / polaris-portal：Feign 调 sso-account 内部 API</li>
 * </ul>
 *
 * <p>默认方法 {@link #canAccessOrg(String)} 复用 {@link DataScopeHelper}，
 * 业务 Service 层注入本接口后可以直接判断单条记录是否可见。
 */
public interface DataScope {

    /** 当前登录人的数据权限根组织编码（可能为 null 表示未配置） */
    String currentScopeCode();

    /** 取指定 accountId 的数据权限根组织编码 */
    String scopeCodeOf(Long accountId);

    /**
     * 默认判断方法：一条数据行归属的 orgCode 是否在当前账号的 scopeCode 范围内。
     * 空 scopeCode = 未配置，返回 false（安全兜底空集）。
     */
    default boolean canAccessOrg(String orgCode) {
        return DataScopeHelper.canAccessOrg(currentScopeCode(), orgCode);
    }
}
