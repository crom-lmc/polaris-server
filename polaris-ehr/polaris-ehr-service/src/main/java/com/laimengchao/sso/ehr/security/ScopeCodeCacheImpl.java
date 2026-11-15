package com.laimengchao.sso.ehr.security;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * polaris-ehr 模块的 scopeCode 缓存实现。
 * <p>
 * 三级链路：ThreadLocal（LoginContextInterceptor 填） → Redis → Feign 调 sso-account。
 * 数据所有者是 sso-account，本模块不查 sso_account 表。
 */
@Component
@RequiredArgsConstructor
public class ScopeCodeCacheImpl implements ScopeCodeCache {

    private final StringRedisTemplate redis;
    private final AccountApi accountApi;

    @Override
    public String get(Long accountId) {
        if (accountId == null) return null;
        // 1. 先试 ThreadLocal（LoginContextInterceptor 已填）
        AccountContext.AccountInfo info = AccountContext.get();
        if (info != null && info.getDataScopeOrgCode() != null) {
            return info.getDataScopeOrgCode();
        }
        // 2. 再试 Redis
        String cached = redis.opsForValue().get(KEY_PREFIX + accountId);
        if (cached != null) return cached;
        // 3. 兜底 Feign 调 sso-account
        R<String> resp = accountApi.dataScopeCode(accountId);
        String fresh = resp == null || !resp.ok() ? null : resp.getData();
        put(accountId, fresh);
        return fresh;
    }

    @Override
    public void put(Long accountId, String scopeCode) {
        if (accountId == null) return;
        if (scopeCode == null) {
            redis.delete(KEY_PREFIX + accountId);
        } else {
            redis.opsForValue().set(KEY_PREFIX + accountId, scopeCode,
                    DEFAULT_TTL_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    public void invalidate(Long accountId) {
        if (accountId == null) return;
        redis.delete(KEY_PREFIX + accountId);
    }
}
