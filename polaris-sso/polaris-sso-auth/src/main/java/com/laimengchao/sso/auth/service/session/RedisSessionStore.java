package com.laimengchao.sso.auth.service.session;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 版会话存储。多实例部署时共享，auth 重启不丢登录态。
 */
public class RedisSessionStore implements SessionStore {

    private static final String PREFIX = "sso:sess:";

    private final StringRedisTemplate redis;

    public RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String sid, Long accountId, long ttlSeconds) {
        redis.opsForValue().set(PREFIX + sid, String.valueOf(accountId), Duration.ofSeconds(ttlSeconds));
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
        redis.delete(PREFIX + sid);
    }
}
