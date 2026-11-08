package com.laimengchao.sso.auth.service.oauth;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 版授权码存储。{@code getAndDelete} 原子取删，并发下只有一个请求能换到令牌。
 * 字段用 \u0001 分隔——URL/scope/编码里不会出现这个控制字符，无需转义。
 */
public class RedisOAuthCodeStore implements OAuthCodeStore {

    private static final String PREFIX = "sso:oauth:code:";
    private static final String SEP = "\u0001";

    private final StringRedisTemplate redis;

    public RedisOAuthCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String code, CodePayload payload, long ttlSeconds) {
        String value = String.join(SEP,
                String.valueOf(payload.accountId()),
                nvl(payload.clientId()),
                nvl(payload.scope()),
                nvl(payload.redirectUri()),
                nvl(payload.codeChallenge()),
                nvl(payload.codeChallengeMethod()),
                nvl(payload.sid()));
        redis.opsForValue().set(PREFIX + code, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public CodePayload take(String code) {
        String v = redis.opsForValue().getAndDelete(PREFIX + code);
        if (v == null) {
            return null;
        }
        String[] parts = v.split(SEP, -1);
        // 兼容滚动升级期间遗留的六段式授权码
        if (parts.length < 6) {
            return null;
        }
        try {
            String sid = parts.length >= 7 && !parts[6].isBlank() ? parts[6] : null;
            return new CodePayload(Long.valueOf(parts[0]), parts[1], parts[2],
                    parts[3], parts[4], parts[5], sid);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}
