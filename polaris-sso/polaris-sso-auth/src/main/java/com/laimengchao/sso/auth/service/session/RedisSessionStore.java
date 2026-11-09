package com.laimengchao.sso.auth.service.session;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 版会话存储。多实例部署时共享，auth 重启不丢登录态。
 */
public class RedisSessionStore implements SessionStore {

    private static final String PREFIX = "sso:sess:";
    private static final String ACCOUNT_PREFIX = "sso:sess:acct:";

    private final StringRedisTemplate redis;

    public RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String sid, Long accountId, long ttlSeconds) {
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        redis.opsForValue().set(PREFIX + sid, String.valueOf(accountId), ttl);
        String accountKey = ACCOUNT_PREFIX + accountId;
        redis.opsForSet().add(accountKey, sid);
        redis.expire(accountKey, ttl);
    }

    @Override
    public Long get(String sid) {
        String v = redis.opsForValue().get(PREFIX + sid);
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void remove(String sid) {
        Long accountId = get(sid);
        redis.delete(PREFIX + sid);
        if (accountId != null) {
            redis.opsForSet().remove(ACCOUNT_PREFIX + accountId, sid);
        }
    }

    @Override
    public java.util.Set<String> sidsOf(Long accountId) {
        java.util.Set<String> members = redis.opsForSet().members(ACCOUNT_PREFIX + accountId);
        if (members == null || members.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.Set<String> alive = new java.util.HashSet<>();
        for (String sid : members) {
            if (Boolean.TRUE.equals(redis.hasKey(PREFIX + sid))) {
                alive.add(sid);
            } else {
                redis.opsForSet().remove(ACCOUNT_PREFIX + accountId, sid);
            }
        }
        return alive;
    }
}
