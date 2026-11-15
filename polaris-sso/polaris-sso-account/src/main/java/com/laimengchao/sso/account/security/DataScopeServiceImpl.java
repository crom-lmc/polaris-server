package com.laimengchao.sso.account.security;

import com.laimengchao.sso.common.security.DataScope;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * sso-account 模块的 DataScope 接口实现。
 * <p>
 * scopeCode 通过 ScopeCodeCacheImpl 拿（ThreadLocal → Redis → 本库 DB），
 * 本服务持有 sso_account 表，无需跨服务 Feign。
 */
@Service
@RequiredArgsConstructor
public class DataScopeServiceImpl implements DataScope {

    private final ScopeCodeCache scopeCodeCache;

    @Override
    public String currentScopeCode() {
        Long accountId = AccountContext.getAccountId();
        return accountId == null ? null : scopeCodeCache.get(accountId);
    }

    @Override
    public String scopeCodeOf(Long accountId) {
        return scopeCodeCache.get(accountId);
    }
}
