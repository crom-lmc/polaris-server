package com.laimengchao.sso.openapi.module.outbound.resilience;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.openapi.common.OpenapiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 出站重试 + 熔断（进程内轻量实现，风格对齐 P0 的 RateLimiter/NonceCache）。
 * <p>
 * 设计文档 §8 建议 Resilience4j，但本项目离线仓库无对应 jar、且约定「不引入与现状不符的新依赖」，
 * 故用同风格的进程内实现；多实例部署时可在 P2 平滑替换为 Resilience4j / Redis，接口不变。
 * <ul>
 *   <li>重试：仅对可重试异常（连接失败/超时/5xx）按指数退避重试，次数取渠道 retry_max；</li>
 *   <li>熔断：按渠道统计滑动窗口失败率，超阈值后短路一段时间（CIRCUIT_OPEN），半开后放行一次探测。</li>
 * </ul>
 */
@Slf4j
@Component
public class OutboundResilience {

    @Value("${openapi.outbound.retry-interval-ms:200}")
    private long retryIntervalMs;
    @Value("${openapi.outbound.circuit-failure-rate:50}")
    private int failureRateThreshold;
    @Value("${openapi.outbound.circuit-min-calls:5}")
    private int minimumCalls;
    @Value("${openapi.outbound.circuit-open-ms:30000}")
    private long openDurationMs;

    private final Map<String, Breaker> breakers = new ConcurrentHashMap<>();

    /** 在熔断 + 重试保护下执行出站调用。 */
    public <T> T execute(String channel, int retryMax, Supplier<T> call) {
        Breaker breaker = breakers.computeIfAbsent(channel, k -> new Breaker());
        breaker.ensureClosed();
        if (!breaker.allow()) {
            log.warn("出站渠道熔断中，短路调用：channel={}", channel);
            throw new BizException(OpenapiErrorCode.CIRCUIT_OPEN);
        }

        int maxAttempts = Math.max(0, retryMax) + 1;
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = call.get();
                breaker.recordSuccess();
                return result;
            } catch (BizException e) {
                // 业务异常（如凭证缺失/白名单拒绝）是确定性失败，不重试，但计入熔断失败率
                breaker.recordFailure();
                if (!isRetryable(e)) {
                    throw e;
                }
                last = e;
            } catch (Exception e) {
                breaker.recordFailure();
                last = e;
            }
            if (attempt < maxAttempts) {
                sleep(retryIntervalMs * (1L << (attempt - 1))); // 指数退避
            }
        }
        log.error("出站调用重试耗尽：channel={} attempts={}", channel, maxAttempts, last);
        throw new BizException(OpenapiErrorCode.SEND_FAILED,
                "出站调用失败（重试 " + maxAttempts + " 次）：" + (last == null ? "" : last.getMessage()));
    }

    /** 仅网络/渠道/超时类错误码可重试；配置类业务错误码不重试。 */
    private boolean isRetryable(BizException e) {
        return e.getErrorCode() == OpenapiErrorCode.SEND_FAILED
                || e.getErrorCode() == OpenapiErrorCode.CHANNEL_ERROR
                || e.getErrorCode() == OpenapiErrorCode.TIMEOUT;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /** 极简滑动窗口熔断器：统计最近 minimumCalls 次调用的失败率。 */
    private final class Breaker {
        private static final int WINDOW = 20;
        private int[] results = new int[WINDOW];
        private int idx = 0;
        private int filled = 0;
        private volatile boolean open = false;
        private volatile long openUntil = 0;
        private volatile boolean halfOpenProbe = false;
        private boolean probeUsed = false;

        synchronized void ensureClosed() {
            if (open && System.currentTimeMillis() >= openUntil) {
                open = false;
                halfOpenProbe = true; // 半开：放行一次探测
                resetWindow();
                log.info("出站渠道进入半开探测：window reset");
            }
        }

        synchronized boolean allow() {
            if (open) {
                return false;
            }
            if (halfOpenProbe) {
                // 半开状态只放行一次，其余快速失败
                if (probeUsed) {
                    return false;
                }
                probeUsed = true;
            }
            return true;
        }

        synchronized void recordSuccess() {
            if (halfOpenProbe) {
                open = false;
                halfOpenProbe = false;
                probeUsed = false;
                resetWindow();
                log.info("出站渠道半开探测成功，恢复闭合");
                return;
            }
            record(0);
        }

        synchronized void recordFailure() {
            if (halfOpenProbe) {
                // 探测失败，重新熔断
                open = true;
                openUntil = System.currentTimeMillis() + openDurationMs;
                halfOpenProbe = false;
                probeUsed = false;
                log.warn("出站渠道半开探测失败，重新熔断 {} ms", openDurationMs);
                return;
            }
            record(1);
            evaluate();
        }

        private void record(int fail) {
            results[idx] = fail;
            idx = (idx + 1) % WINDOW;
            if (filled < WINDOW) {
                filled++;
            }
        }

        private void evaluate() {
            if (filled < minimumCalls) {
                return;
            }
            int fails = 0;
            for (int i = 0; i < filled; i++) {
                fails += results[i];
            }
            int rate = fails * 100 / filled;
            if (rate >= failureRateThreshold) {
                open = true;
                openUntil = System.currentTimeMillis() + openDurationMs;
                log.warn("出站渠道触发熔断：失败率 {}% >= {}%（样本 {}），熔断 {} ms",
                        rate, failureRateThreshold, filled, openDurationMs);
            }
        }

        private void resetWindow() {
            results = new int[WINDOW];
            idx = 0;
            filled = 0;
        }
    }
}
