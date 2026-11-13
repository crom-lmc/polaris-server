package com.laimengchao.sso.common.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量级单机限流/失败计数（滑动窗口），用于登录失败锁定、验证码发送频率等场景。
 * <p>
 * 只适合单实例部署；多实例时各节点独立计数，需替换为 Redis 实现。
 * 键与窗口由调用方决定，计数在窗口结束后自动归零。
 */
public final class InMemoryRateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /** 当前窗口内是否已达到上限。 */
    public boolean isBlocked(String key, int limit) {
        Counter counter = counters.get(key);
        if (counter == null) {
            return false;
        }
        if (expired(counter, System.currentTimeMillis())) {
            counters.remove(key, counter);
            return false;
        }
        return counter.count >= limit;
    }

    /** 记录一次计数；窗口过期后重新开始。 */
    public void record(String key, long windowMillis) {
        long now = System.currentTimeMillis();
        counters.compute(key, (k, counter) -> {
            Counter current = counter;
            if (current == null || expired(current, now)) {
                current = new Counter();
                current.windowStart = now;
                current.windowMillis = windowMillis;
            }
            current.count++;
            return current;
        });
        if (counters.size() > CLEANUP_THRESHOLD) {
            counters.entrySet().removeIf(entry -> expired(entry.getValue(), now));
        }
    }

    /** 成功后清零，例如登录成功解除账号维度的失败计数。 */
    public void reset(String key) {
        counters.remove(key);
    }

    private boolean expired(Counter counter, long now) {
        return now - counter.windowStart >= counter.windowMillis;
    }

    private static final class Counter {
        private int count;
        private long windowStart;
        private long windowMillis;
    }
}
