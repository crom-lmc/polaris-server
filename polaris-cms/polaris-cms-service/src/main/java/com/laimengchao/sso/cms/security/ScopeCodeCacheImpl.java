package com.laimengchao.sso.cms.security;

import com.laimengchao.sso.client.api.AccountApi;
import com.laimengchao.sso.client.dto.R;
import com.laimengchao.sso.common.security.ScopeCodeCache;
import com.laimengchao.sso.utils.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * polaris-cms 模块的 scopeCode 缓存实现。
 * 三级链路：ThreadLocal → Redis → Feign 调 sso-account。
 */
@Slf4j
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
        try {
            String cached = redis.opsForValue().get(KEY_PREFIX + accountId);
            if (cached != null) return cached;
        } catch (DataAccessException e) {
            log.warn("读取 CMS 数据权限缓存失败，降级查询账号中心，accountId={}，原因={}", accountId, e.getMessage());
        }
        R<String> resp = accountApi.dataScopeCode(accountId);
        String fresh = resp == null || !resp.ok() ? null : resp.getData();
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
            log.warn("写入 CMS 数据权限缓存失败，accountId={}，原因={}", accountId, e.getMessage());
        }
    }

    @Override
    public void invalidate(Long accountId) {
        if (accountId == null) return;
        try {
            redis.delete(KEY_PREFIX + accountId);
        } catch (DataAccessException e) {
            log.warn("清除 CMS 数据权限缓存失败，accountId={}，原因={}", accountId, e.getMessage());
        }
    }
}
