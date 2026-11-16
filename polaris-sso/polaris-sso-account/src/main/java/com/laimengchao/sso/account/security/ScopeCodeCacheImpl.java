package com.laimengchao.sso.account.security;

import com.laimengchao.sso.account.mapper.SsoAccountMapper;
import com.laimengchao.sso.account.domain.entity.SsoAccount;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * sso-account 模块的 scopeCode 缓存实现。
 * <p>
 * 三级链路：ThreadLocal（LoginContextInterceptor 填） → Redis → 本库 DB。
 * 本服务持有 sso_account 表，无需跨服务 Feign。
 */
@Slf4j
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
        // 2. 再试 Redis；缓存不可用不能阻断业务查询
        try {
            String cached = redis.opsForValue().get(KEY_PREFIX + accountId);
            if (cached != null) return cached;
        } catch (DataAccessException e) {
            log.warn("读取账号数据权限缓存失败，降级查询本库，accountId={}，原因={}", accountId, e.getMessage());
        }
        // 3. 兜底查本库 DB
        SsoAccount acc = accountMapper.selectById(accountId);
        String fresh = acc == null ? null : acc.getDataScopeOrgCode();
        put(accountId, fresh);
        return fresh;
    }

    @Override
    public void put(Long accountId, String scopeCode) {
        if (accountId == null) return;
        try {
            if (scopeCode == null) {
                redis.delete(KEY_PREFIX + accountId);
            } else {
                redis.opsForValue().set(KEY_PREFIX + accountId, scopeCode,
                        DEFAULT_TTL_MINUTES, TimeUnit.MINUTES);
            }
        } catch (DataAccessException e) {
            log.warn("写入账号数据权限缓存失败，accountId={}，原因={}", accountId, e.getMessage());
        }
    }

    @Override
    public void invalidate(Long accountId) {
        if (accountId == null) return;
        try {
            redis.delete(KEY_PREFIX + accountId);
        } catch (DataAccessException e) {
            log.warn("清除账号数据权限缓存失败，accountId={}，原因={}", accountId, e.getMessage());
        }
    }
}
