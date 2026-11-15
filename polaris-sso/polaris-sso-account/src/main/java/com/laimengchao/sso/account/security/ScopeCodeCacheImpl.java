package com.laimengchao.sso.account.security;

import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * sso-account 模块的 scopeCode 缓存实现。
 * <p>
 * 三级链路：ThreadLocal（LoginContextInterceptor 填） → Redis → 本库 DB。
 * 本服务持有 sso_account 表，无需跨服务 Feign。
 */
@Component
@RequiredArgsConstructor
public class ScopeCodeCacheImpl implements ScopeCodeCache {

    private final StringRedisTemplate redis;
    private final SsoAccountMapper accountMapper;

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
        // 3. 兜底查本库 DB
        SsoAccount acc = accountMapper.selectById(accountId);
        String fresh = acc == null ? null : acc.getDataScopeOrgCode();
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
