package com.laimengchao.sso.auth.service.ticket;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Redis 版票据存储。{@code getAndDelete} 本身是原子的，
 * 并发下两张请求只有一张能取到票据，天然防重放。
 */
public class RedisTicketStore implements TicketStore {

    private static final String PREFIX = "sso:ticket:";
    private static final String SEP = "\u0001";

    private final StringRedisTemplate redis;

    public RedisTicketStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String ticket, TicketPayload payload, long ttlSeconds) {
        // 第三段是 sid；为空时留空串，保证段数固定，解析简单
        String value = payload.accountId() + SEP + payload.appCode()
                + SEP + (payload.sid() == null ? "" : payload.sid());
        redis.opsForValue().set(PREFIX + ticket, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public TicketPayload take(String ticket) {
        String v = redis.opsForValue().getAndDelete(PREFIX + ticket);
        if (v == null) {
            return null;
        }
        String[] parts = v.split(SEP);
        // 兼容滚动升级期间遗留的两段式票据
        if (parts.length < 2) {
            return null;
        }
        try {
            String sid = parts.length >= 3 && !parts[2].isBlank() ? parts[2] : null;
            return new TicketPayload(Long.valueOf(parts[0]), parts[1], sid);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
