package com.laimengchao.sso.openapi.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 可用性支撑：探活 + 降级开关。
 * <p>
 * P2 的限流/nonce 优先走 Redis 实现跨实例共享，Redis 不可用（本地未启动/网络故障）时
 * 自动回退到 P0 的进程内实现——开放平台不能因为缓存故障就把所有调用方挡在门外（fail-open）。
 * 探活结果做短周期缓存，避免每次请求都因连不上 Redis 而卡 3 秒。
 */
@Slf4j
@Component
public class RedisSupport {

    private final ObjectProvider<StringRedisTemplate> templateProvider;
    /** 探活结果缓存：下次探活的最早时间（毫秒） */
    private final AtomicLong nextProbe = new AtomicLong(0);
    private volatile boolean alive = false;
    /** 探活间隔（毫秒） */
    private static final long PROBE_INTERVAL_MS = 5_000;

    public RedisSupport(ObjectProvider<StringRedisTemplate> templateProvider) {
        this.templateProvider = templateProvider;
    }

    /** 返回可用的 Redis 模板；不可用返回 null，调用方据此降级。 */
    public StringRedisTemplate template() {
        long now = System.currentTimeMillis();
        if (now >= nextProbe.get()) {
            probe(now);
        }
        return alive ? templateProvider.getIfAvailable() : null;
    }

    private void probe(long now) {
        StringRedisTemplate tpl = templateProvider.getIfAvailable();
        boolean ok = false;
        if (tpl != null) {
            try {
                tpl.hasKey("__openapi_probe__");
                ok = true;
            } catch (Exception e) {
                log.debug("Redis 探活失败，降级进程内实现：{}", e.getMessage());
            }
        }
        if (ok != alive) {
            if (ok) {
                log.info("Redis 已连接，限流/nonce 切换为跨实例共享实现");
            } else {
                log.warn("Redis 不可用，限流/nonce 降级为进程内实现（多实例下各实例独立计数）");
            }
        }
        alive = ok;
        nextProbe.set(now + PROBE_INTERVAL_MS);
    }
}
