package com.laimengchao.sso.openapi.security;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import com.laimengchao.sso.openapi.module.setting.service.SettingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 appCode + apiCode 的每分钟限流。
 * <p>
 * P2：优先用 Redis + Lua 原子「自增 + 首次设过期」实现跨实例共享；Redis 不可用时
 * 自动降级到 P0 的进程内滑动窗口（见 {@link RedisSupport}），契约不变。
 */
@Slf4j
@Component
public class RateLimiter {

    private static final String KEY_PREFIX = "openapi:rate:";
    /** INCR 后首次设置 EXPIRE，返回是否未超限（1=放行 0=超限），整段原子 */
    private static final DefaultRedisScript<Long> INCR_AND_EXPIRE = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); " +
            "if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; " +
            "if n > tonumber(ARGV[2]) then return 0 else return 1 end", Long.class);

    private final RedisSupport redisSupport;
    private final SettingService settingService;

    /** 进程内降级窗口 */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(RedisSupport redisSupport, SettingService settingService) {
        this.redisSupport = redisSupport;
        this.settingService = settingService;
    }

    /** 超过 limit 抛 429。全局限流（若配置）与 endpoint 级 limit 取更严格者。 */
    public void check(String appCode, String apiCode, int limit) {
        int global = globalLimit();
        if (global > 0) {
            limit = limit <= 0 ? global : Math.min(limit, global);
        }
        if (limit <= 0) {
            return;
        }
        StringRedisTemplate redis = redisSupport.template();
        if (redis != null) {
            try {
                long minute = Instant.now().getEpochSecond() / 60;
                String key = KEY_PREFIX + appCode + ":" + apiCode + ":" + minute;
                Long allowed = redis.execute(INCR_AND_EXPIRE, Collections.singletonList(key),
                        String.valueOf(60), String.valueOf(limit));
                if (allowed != null && allowed == 0L) {
                    throw new BizException(OpenapiErrorCode.RATE_LIMITED);
                }
                return;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Redis 限流调用失败，降级进程内：{}", e.getMessage());
                // 落到下面的进程内实现
            }
        }
        checkLocal(appCode, apiCode, limit);
    }

    /** 读取全局每应用每分钟上限；空/非法配置返回 0（不限）。SettingService 内部带 10s 缓存。 */
    private int globalLimit() {
        try {
            String v = settingService.get(SettingService.KEY_GLOBAL_RATE_LIMIT, "");
            if (v == null || v.isBlank()) {
                return 0;
            }
            int n = Integer.parseInt(v.trim());
            return n > 0 ? n : 0;
        } catch (NumberFormatException e) {
            log.warn("全局限流配置非法（global.rate.limit={}），忽略", "见库");
            return 0;
        } catch (Exception e) {
            log.warn("读取全局限流配置失败，忽略：{}", e.getMessage());
            return 0;
        }
    }

    /** 进程内降级实现（P0 逻辑）。 */
    private void checkLocal(String appCode, String apiCode, int limit) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = appCode + ":" + apiCode + ":" + minute;
        Window w = windows.computeIfAbsent(key, k -> new Window());
        int count = w.incrementAndGet();
        windows.keySet().removeIf(k -> !k.endsWith(":" + minute));
        if (count > limit) {
            throw new BizException(OpenapiErrorCode.RATE_LIMITED);
        }
    }

    private static final class Window {
        private int count;
        synchronized int incrementAndGet() {
            return ++count;
        }
    }
}
