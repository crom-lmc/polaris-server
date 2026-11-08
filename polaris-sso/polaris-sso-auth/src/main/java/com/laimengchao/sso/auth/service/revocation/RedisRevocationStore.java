package com.laimengchao.sso.auth.service.revocation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis 版吊销名单。用 ZSET 存储（member=jti，score=自然过期 epoch 毫秒），
 * 天然支持多实例共享且 auth 重启不丢。网关仍按原协议轮询 /internal/revoked，
 * 但此时读到的是全局共享集合。
 * <p>过期条目在 snapshot / isRevoked 时惰性清理，避免额外定时任务。</p>
 */
@Slf4j
public class RedisRevocationStore implements RevocationStore {

    private static final String KEY = "sso:revoked";
    private final StringRedisTemplate redis;

    public RedisRevocationStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void revoke(String jti, Instant exp) {
        if (jti == null || exp == null) {
            return;
        }
        redis.opsForZSet().add(KEY, jti, exp.toEpochMilli());
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) {
            return false;
        }
        Double score = redis.opsForZSet().score(KEY, jti);
        if (score == null) {
            return false;
        }
        // 已过期：顺手移除，返回未吊销
        if (score < System.currentTimeMillis()) {
            redis.opsForZSet().remove(KEY, jti);
            return false;
        }
        return true;
    }

    @Override
    public Map<String, Instant> snapshot() {
        // 先清掉自然过期的条目
        redis.opsForZSet().removeRangeByScore(KEY, 0, System.currentTimeMillis());
        Set<String> members = redis.opsForZSet().range(KEY, 0, -1);
        Map<String, Instant> out = new HashMap<>();
        if (members != null) {
            for (String jti : members) {
                Double score = redis.opsForZSet().score(KEY, jti);
                if (score != null) {
                    out.put(jti, Instant.ofEpochMilli(score.longValue()));
                }
            }
        }
        return out;
    }
}
