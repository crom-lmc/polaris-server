package com.laimengchao.sso.cms.security;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * polaris-cms 模块的 scopeCode 缓存实现。
 * 三级链路：ThreadLocal → Redis → Feign 调 sso-account。
 */
@Component
@RequiredArgsConstructor
public class ScopeCodeCacheImpl implements ScopeCodeCache {

    private final StringRedisTemplate redis;
    private final AccountApi accountApi;

    @Override
    public String get(Long accountId) {
        if (accountId == null) return null;
        AccountContext.AccountInfo info = AccountContext.get();
        if (info != null && info.getDataScopeOrgCode() != null) {
            return info.getDataScopeOrgCode();
        }
        String cached = redis.opsForValue().get(KEY_PREFIX + accountId);
        if (cached != null) return cached;
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
