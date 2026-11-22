package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.openapi.module.setting.service.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 签名模式 nonce 去重（防重放）。
 * <p>
 * P2：优先用 Redis SETNX + EXPIRE 原子实现跨实例共享；Redis 不可用时降级到 P0 进程内实现。
 */
@Slf4j
@Component
public class NonceCache {

    private static final String KEY_PREFIX = "openapi:nonce:";
    /** SETNX + EXPIRE 原子：首次写入返回 1，重复返回 0 */
    private static final DefaultRedisScript<Long> SETNX_EXPIRE = new DefaultRedisScript<>(
            "if redis.call('SETNX', KEYS[1], '1') == 1 then " +
            "redis.call('EXPIRE', KEYS[1], ARGV[1]); return 1 else return 0 end", Long.class);

    private final RedisSupport redisSupport;
    private final long defaultWindowSeconds;
    private final SettingService settingService;

    /** 进程内降级 */
    private final Map<String, Long> seen = new ConcurrentHashMap<>();
    private volatile long lastCleanup = 0;

    public NonceCache(RedisSupport redisSupport,
                      @Value("${openapi.signature-tolerance-seconds:300}") long windowSeconds,
                      SettingService settingService) {
        this.redisSupport = redisSupport;
        this.defaultWindowSeconds = windowSeconds;
        this.settingService = settingService;
    }

    /** 有效去重窗口：平台设置 nonce.ttl.seconds 优先，空/非法时回退到签名容忍窗口。 */
    private long windowSeconds() {
        try {
            String v = settingService.get(SettingService.KEY_NONCE_TTL, "");
            if (v != null && !v.isBlank()) {
                long n = Long.parseLong(v.trim());
                if (n > 0) {
                    return n;
                }
            }
        } catch (NumberFormatException e) {
            log.warn("nonce.ttl.seconds 配置非法，回退默认值 {}：{}", defaultWindowSeconds, e.getMessage());
        } catch (Exception e) {
            log.warn("读取 nonce.ttl.seconds 失败，回退默认值：{}", e.getMessage());
        }
        return defaultWindowSeconds;
    }

    /** 首次见到返回 true；重复或已标记返回 false。 */
    public boolean markOnce(String appCode, String nonce) {
        long window = windowSeconds();
        StringRedisTemplate redis = redisSupport.template();
        if (redis != null) {
            try {
                String key = KEY_PREFIX + appCode + ":" + nonce;
                Long added = redis.execute(SETNX_EXPIRE, Collections.singletonList(key),
                        String.valueOf(window));
                return added != null && added == 1L;
            } catch (Exception e) {
                log.warn("Redis nonce 去重调用失败，降级进程内：{}", e.getMessage());
                // 落到下面的进程内实现
            }
        }
        return markOnceLocal(appCode, nonce, window);
    }

    private boolean markOnceLocal(String appCode, String nonce, long window) {
        long now = Instant.now().getEpochSecond();
        if (now - lastCleanup > window) {
            cleanup(now);
            lastCleanup = now;
        }
        String key = appCode + ":" + nonce;
        Long prev = seen.putIfAbsent(key, now + window);
        if (prev == null) {
            return true;
        }
        if (prev <= now) {
            seen.put(key, now + window);
            return true;
        }
        return false;
    }

    private void cleanup(long now) {
        seen.entrySet().removeIf(e -> e.getValue() <= now);
    }
}
